package org.yatopiamc.c2me.mixin.threading.chunkio;

import com.ibm.asyncutil.locks.AsyncNamedLock;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Either;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.concurrent.ThreadTaskExecutor;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.palette.UpgradeData;
import net.minecraft.village.PointOfInterestManager;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.chunk.storage.ChunkLoader;
import net.minecraft.world.chunk.storage.ChunkSerializer;
import net.minecraft.world.gen.feature.structure.StructureStart;
import net.minecraft.world.gen.feature.template.TemplateManager;
import net.minecraft.world.server.ChunkHolder;
import net.minecraft.world.server.ChunkManager;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.DimensionSavedDataManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.yatopiamc.c2me.common.threading.chunkio.AsyncSerializationManager;
import org.yatopiamc.c2me.common.threading.chunkio.C2MECachedRegionStorage;
import org.yatopiamc.c2me.common.threading.chunkio.ChunkIoMainThreadTaskUtils;
import org.yatopiamc.c2me.common.threading.chunkio.ChunkIoThreadingExecutorUtils;
import org.yatopiamc.c2me.common.threading.chunkio.IAsyncChunkStorage;
import org.yatopiamc.c2me.common.threading.chunkio.ISerializingRegionBasedStorage;
import org.yatopiamc.c2me.common.util.SneakyThrow;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

@Mixin(ChunkManager.class)
public abstract class MixinThreadedAnvilChunkStorage extends ChunkLoader implements ChunkHolder.IPlayerProvider {

    public MixinThreadedAnvilChunkStorage(File file, DataFixer dataFixer, boolean bl) {
        super(file, dataFixer, bl);
    }

    @Shadow
    @Final
    private ServerWorld level;

    @Shadow
    @Final
    private TemplateManager structureManager;

    @Shadow
    @Final
    private PointOfInterestManager poiManager;

    @Shadow
    protected abstract byte markPosition(ChunkPos chunkPos, ChunkStatus.Type chunkType);

    @Shadow
    @Final
    private static Logger LOGGER;

    @Shadow
    protected abstract void markPositionReplaceable(ChunkPos chunkPos);

    @Shadow
    @Final
    private Supplier<DimensionSavedDataManager> overworldDataStorage;

    @Shadow
    @Final
    private ThreadTaskExecutor<Runnable> mainThreadExecutor;

    @Shadow
    protected abstract boolean isExistingChunkFull(ChunkPos chunkPos);

    private AsyncNamedLock<ChunkPos> chunkLock = AsyncNamedLock.createFair();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo info) {
        chunkLock = AsyncNamedLock.createFair();
    }

    private Set<ChunkPos> scheduledChunks = new HashSet<>();

    /**
     * @author ishland
     * @reason async io and deserialization
     */
    @Overwrite
    private CompletableFuture<Either<IChunk, ChunkHolder.IChunkLoadingError>> scheduleChunkLoad(ChunkPos pos) {
        if (scheduledChunks == null) scheduledChunks = new HashSet<>();
        synchronized (scheduledChunks) {
            if (scheduledChunks.contains(pos)) throw new IllegalArgumentException("Already scheduled");
            scheduledChunks.add(pos);
        }

        final CompletableFuture<CompoundNBT> poiData = ((IAsyncChunkStorage) this.poiManager.worker).getNbtAtAsync(pos);

        final CompletableFuture<Either<IChunk, ChunkHolder.IChunkLoadingError>> future = getUpdatedChunkTagAtAsync(pos).thenApplyAsync(compoundTag -> {
            if (compoundTag != null) {
                try {
                    if (compoundTag.contains("Level", 10) && compoundTag.getCompound("Level").contains("Status", 8)) {
                        return ChunkSerializer.read(this.level, this.structureManager, this.poiManager, pos, compoundTag);
                    }

                    LOGGER.warn("Chunk file at {} is missing level data, skipping", pos);
                } catch (Throwable t) {
                    LOGGER.error("Couldn't load chunk {}, chunk data will be lost!", pos, t);
                }
            }
            return null;
        }, ChunkIoThreadingExecutorUtils.serializerExecutor).thenCombine(poiData, (protoChunk, tag) -> protoChunk).thenApplyAsync(protoChunk -> {
            ((ISerializingRegionBasedStorage) this.poiManager).update(pos, poiData.join());
            ChunkIoMainThreadTaskUtils.drainQueue();
            if (protoChunk != null) {
                protoChunk.setLastSaveTime(this.level.getGameTime());
                this.markPosition(pos, protoChunk.getStatus().getChunkType());
                return Either.left(protoChunk);
            } else {
                this.markPositionReplaceable(pos);
                return Either.left(new ChunkPrimer(pos, UpgradeData.EMPTY));
            }
        }, this.mainThreadExecutor);
        future.exceptionally(throwable -> null).thenRun(() -> {
            synchronized (scheduledChunks) {
                scheduledChunks.remove(pos);
            }
        });
        return future;

        // [VanillaCopy] - for reference
        /*
        return CompletableFuture.supplyAsync(() -> {
            try {
                CompoundTag compoundTag = this.getUpdatedChunkTag(pos);
                if (compoundTag != null) {
                    boolean bl = compoundTag.contains("Level", 10) && compoundTag.getCompound("Level").contains("Status", 8);
                    if (bl) {
                        Chunk chunk = ChunkSerializer.deserialize(this.world, this.structureManager, this.pointOfInterestStorage, pos, compoundTag);
                        chunk.setLastSaveTime(this.world.getTime());
                        this.method_27053(pos, chunk.getStatus().getChunkType());
                        return Either.left(chunk);
                    }

                    LOGGER.error((String)"Chunk file at {} is missing level data, skipping", (Object)pos);
                }
            } catch (CrashException var5) {
                Throwable throwable = var5.getCause();
                if (!(throwable instanceof IOException)) {
                    this.method_27054(pos);
                    throw var5;
                }

                LOGGER.error("Couldn't load chunk {}", pos, throwable);
            } catch (Exception var6) {
                LOGGER.error("Couldn't load chunk {}", pos, var6);
            }

            this.method_27054(pos);
            return Either.left(new ProtoChunk(pos, UpgradeData.NO_UPGRADE_DATA));
        }, this.mainThreadExecutor);
         */
    }

    private CompletableFuture<CompoundNBT> getUpdatedChunkTagAtAsync(ChunkPos pos) {
        return chunkLock.acquireLock(pos).toCompletableFuture().thenCompose(lockToken -> ((IAsyncChunkStorage) this.worker).getNbtAtAsync(pos).thenApply(compoundTag -> {
            if (compoundTag != null)
                return this.upgradeChunkTag(this.level.dimension(), this.overworldDataStorage, compoundTag);
            else return null;
        }).handle((tag, throwable) -> {
            lockToken.releaseLock();
            if (throwable != null)
                SneakyThrow.sneaky(throwable);
            return tag;
        }));
    }

    private ConcurrentLinkedQueue<CompletableFuture<Void>> saveFutures = new ConcurrentLinkedQueue<>();

    @Dynamic
    @Redirect(method = {"lambda$scheduleUnload$10", "func_219185_a"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/server/ChunkManager;save(Lnet/minecraft/world/chunk/IChunk;)Z")) // method: consumer in tryUnloadChunk
    private boolean asyncSave(ChunkManager tacs, IChunk p_219229_1_) {
        // TODO [VanillaCopy] - check when updating minecraft version
        this.poiManager.flush(p_219229_1_.getPos());
        if (!p_219229_1_.isUnsaved()) {
            return false;
        } else {
            p_219229_1_.setLastSaveTime(this.level.getGameTime());
            p_219229_1_.setUnsaved(false);
            ChunkPos chunkpos = p_219229_1_.getPos();

            try {
                ChunkStatus chunkstatus = p_219229_1_.getStatus();
                if (chunkstatus.getChunkType() != ChunkStatus.Type.LEVELCHUNK) {
                    if (this.isExistingChunkFull(chunkpos)) {
                        return false;
                    }

                    if (chunkstatus == ChunkStatus.EMPTY && p_219229_1_.getAllStarts().values().stream().noneMatch(StructureStart::isValid)) {
                        return false;
                    }
                }

                this.level.getProfiler().incrementCounter("chunkSave");
                // C2ME start - async serialization
                if (saveFutures == null) saveFutures = new ConcurrentLinkedQueue<>();
                AsyncSerializationManager.Scope scope = new AsyncSerializationManager.Scope(p_219229_1_, level);

                saveFutures.add(chunkLock.acquireLock(p_219229_1_.getPos()).toCompletableFuture().thenCompose(lockToken ->
                        CompletableFuture.supplyAsync(() -> {
                            scope.open();
                            AsyncSerializationManager.push(scope);
                            try {
                                return ChunkSerializer.write(this.level, p_219229_1_);
                            } finally {
                                AsyncSerializationManager.pop(scope);
                            }
                        }, ChunkIoThreadingExecutorUtils.serializerExecutor)
                                .thenAcceptAsync(compoundTag -> {
                                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.ChunkDataEvent.Save(p_219229_1_, p_219229_1_.getWorldForge() != null ? p_219229_1_.getWorldForge() : this.level, compoundTag)); // [VanillaCopy]
                                    this.write(chunkpos, compoundTag);
                                }, this.mainThreadExecutor)
                                .handle((unused, throwable) -> {
                                    lockToken.releaseLock();
                                    if (throwable != null)
                                        LOGGER.error("Failed to save chunk {},{}", chunkpos.x, chunkpos.z, throwable);
                                    return unused;
                                })));
                this.markPosition(chunkpos, chunkstatus.getChunkType());
                // C2ME end
                return true;
            } catch (Exception exception) {
                LOGGER.error("Failed to save chunk {},{}", chunkpos.x, chunkpos.z, exception);
                return false;
            }
        }
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
    private void onTick(CallbackInfo info) {
        ChunkIoThreadingExecutorUtils.serializerExecutor.execute(() -> saveFutures.removeIf(CompletableFuture::isDone));
    }

    @Override
    public void flushWorker() {
        final CompletableFuture<Void> future = CompletableFuture.allOf(saveFutures.toArray(new CompletableFuture[0]));
        this.mainThreadExecutor.managedBlock(future::isDone); // wait for serialization to complete
        super.flushWorker();
    }
}
