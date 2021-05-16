package org.yatopiamc.c2me.mixin.threading.chunkio;

import com.mojang.datafixers.util.Either;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.storage.IOWorker;
import net.minecraft.world.chunk.storage.RegionFileCache;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.yatopiamc.c2me.common.threading.chunkio.C2MECachedRegionStorage;
import org.yatopiamc.c2me.common.threading.chunkio.ChunkIoThreadingExecutorUtils;
import org.yatopiamc.c2me.common.threading.chunkio.IAsyncChunkStorage;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Mixin(IOWorker.class)
public abstract class MixinStorageIoWorker implements IAsyncChunkStorage {

    @Shadow @Final private AtomicBoolean shutdownRequested;

    @Shadow @Final private static Logger LOGGER;

    @Shadow protected abstract <T> CompletableFuture<T> submitTask(Supplier<Either<T, Exception>> p_235975_1_);

    @Shadow @Final private RegionFileCache storage;

    @Shadow @Final private Map<ChunkPos, IOWorker.Entry> pendingWrites;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onPostInit(CallbackInfo info) {
        //noinspection ConstantConditions
        if (((Object) this) instanceof C2MECachedRegionStorage) {
            this.shutdownRequested.set(true);
        }
    }

    private final AtomicReference<ExecutorService> executorService = new AtomicReference<>();

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;ioPool()Ljava/util/concurrent/Executor;"))
    private Executor onGetStorageIoWorker() {
        executorService.set(Executors.newSingleThreadExecutor(ChunkIoThreadingExecutorUtils.ioWorkerFactory));
        return executorService.get();
    }

    @Inject(method = "close", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/concurrent/DelegatedTaskExecutor;close()V", shift = At.Shift.AFTER))
    private void onClose(CallbackInfo ci) {
        final ExecutorService executorService = this.executorService.get();
        if (executorService != null) executorService.shutdown();
    }

    @Override
    public CompletableFuture<CompoundNBT> getNbtAtAsync(ChunkPos pos) {
        // TODO [VanillaCopy]
        return this.submitTask(() -> {
            IOWorker.Entry result = (IOWorker.Entry)this.pendingWrites.get(pos);
            if (result != null) {
                return Either.left(result.data);
            } else {
                try {
                    CompoundNBT compoundTag = this.storage.read(pos);
                    return Either.left(compoundTag);
                } catch (Exception var4) {
                    LOGGER.warn((String)"Failed to read chunk {}", (Object)pos, (Object)var4);
                    return Either.right(var4);
                }
            }
        });

    }
}
