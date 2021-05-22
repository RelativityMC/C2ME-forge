package org.yatopiamc.c2me.mixin.threading.worldgen;

import com.mojang.datafixers.util.Either;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.feature.template.TemplateManager;
import net.minecraft.world.server.ChunkHolder;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.server.ServerWorldLightManager;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.yatopiamc.c2me.common.config.C2MEConfig;
import org.yatopiamc.c2me.common.threading.worldgen.ChunkStatusUtils;
import org.yatopiamc.c2me.common.threading.worldgen.IChunkStatus;
import org.yatopiamc.c2me.common.threading.worldgen.IWorldGenLockable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

@Mixin(ChunkStatus.class)
public abstract class MixinChunkStatus implements IChunkStatus {

    @Shadow
    @Final
    private ChunkStatus.IGenerationWorker generationTask;

    private int reducedTaskRadius = -1;

    @Shadow @Final private int range;

    public void calculateReducedTaskRadius() {
        if (this.range == 0) {
            this.reducedTaskRadius = 0;
        } else {
            for (int i = 0; i <= this.range; i++) {
                final ChunkStatus status = ChunkStatus.getStatus(ChunkStatus.getDistance((ChunkStatus) (Object) this) + i); // TODO [VanillaCopy] from TACS getRequiredStatusForGeneration
                if (status == ChunkStatus.STRUCTURE_STARTS) {
                    this.reducedTaskRadius = Math.min(this.range, i);
                    break;
                }
            }
        }
        //noinspection ConstantConditions
        if ((Object) this == ChunkStatus.LIGHT) {
            this.reducedTaskRadius = 1;
        }
        System.out.println(String.format("%s task radius: %d -> %d", this, this.range, this.reducedTaskRadius));
    }

    @Dynamic
    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void onCLInit(CallbackInfo info) {
        for (ChunkStatus chunkStatus : Registry.CHUNK_STATUS) {
            ((IChunkStatus) chunkStatus).calculateReducedTaskRadius();
        }
    }

    /**
     * @author ishland
     * @reason take over generation & improve chunk status transition speed
     */
    @Overwrite
    public CompletableFuture<Either<IChunk, ChunkHolder.IChunkLoadingError>> generate(ServerWorld world, ChunkGenerator chunkGenerator, TemplateManager structureManager, ServerWorldLightManager lightingProvider, Function<IChunk, CompletableFuture<Either<IChunk, ChunkHolder.IChunkLoadingError>>> function, List<IChunk> chunks) {
        final IChunk targetChunk = chunks.get(chunks.size() / 2);
        Supplier<CompletableFuture<Either<IChunk, ChunkHolder.IChunkLoadingError>>> generationTask = () ->
                this.generationTask.doWork((ChunkStatus) (Object) this, world, chunkGenerator, structureManager, lightingProvider, function, chunks, targetChunk);
        if (targetChunk.getStatus().isOrAfter((ChunkStatus) (Object) this)) {
            return generationTask.get();
        } else {
            int lockRadius = C2MEConfig.threadedWorldGenConfig.reduceLockRadius && this.reducedTaskRadius != -1 ? this.reducedTaskRadius : this.range;
            //noinspection ConstantConditions
            return ChunkStatusUtils.runChunkGenWithLock(targetChunk.getPos(), lockRadius, ((IWorldGenLockable) world).getWorldGenChunkLock(), () ->
                    ChunkStatusUtils.getThreadingType((ChunkStatus) (Object) this).runTask(((IWorldGenLockable) world).getWorldGenSingleThreadedLock(), generationTask));
        }
    }

}
