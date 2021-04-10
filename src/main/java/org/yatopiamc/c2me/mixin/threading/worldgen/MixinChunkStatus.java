package org.yatopiamc.c2me.mixin.threading.worldgen;

import com.mojang.datafixers.util.Either;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.feature.template.TemplateManager;
import net.minecraft.world.server.ChunkHolder;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.server.ServerWorldLightManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.yatopiamc.c2me.common.threading.worldgen.ChunkStatusUtils;
import org.yatopiamc.c2me.common.threading.worldgen.IWorldGenLockable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Mixin(ChunkStatus.class)
public class MixinChunkStatus {

    @Shadow
    @Final
    private ChunkStatus.IGenerationWorker generationTask;

    @Shadow @Final public static ChunkStatus FEATURES;

    @Shadow @Final private int range;

    /**
     * @author ishland
     * @reason take over generation
     */
    @Overwrite
    public CompletableFuture<Either<IChunk, ChunkHolder.IChunkLoadingError>> generate(ServerWorld world, ChunkGenerator chunkGenerator, TemplateManager structureManager, ServerWorldLightManager lightingProvider, Function<IChunk, CompletableFuture<Either<IChunk, ChunkHolder.IChunkLoadingError>>> function, List<IChunk> chunks) {
        final IChunk targetChunk = chunks.get(chunks.size() / 2);
        //noinspection ConstantConditions
        return ChunkStatusUtils.runChunkGenWithLock(targetChunk.getPos(), this.range, ((IWorldGenLockable) world).getWorldGenChunkLock(), () ->
                ChunkStatusUtils.getThreadingType((ChunkStatus) (Object) this).runTask(((IWorldGenLockable) world).getWorldGenSingleThreadedLock(), () ->
                        this.generationTask.doWork((ChunkStatus) (Object) this, world, chunkGenerator, structureManager, lightingProvider, function, chunks, targetChunk)
                )
        );
    }

}
