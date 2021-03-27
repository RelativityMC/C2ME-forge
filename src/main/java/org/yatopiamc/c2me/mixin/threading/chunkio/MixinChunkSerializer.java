package org.yatopiamc.c2me.mixin.threading.chunkio;

import net.minecraft.block.Block;
import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.village.PointOfInterestManager;
import net.minecraft.world.ITickList;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.chunk.storage.ChunkSerializer;
import net.minecraft.world.lighting.IWorldLightListener;
import net.minecraft.world.lighting.WorldLightManager;
import net.minecraft.world.server.ServerTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.yatopiamc.c2me.common.threading.chunkio.AsyncSerializationManager;
import org.yatopiamc.c2me.common.threading.chunkio.ChunkIoMainThreadTaskUtils;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Mixin(ChunkSerializer.class)
public class MixinChunkSerializer {

    @Redirect(method = "read", at = @At(value = "INVOKE", target = "Lnet/minecraft/village/PointOfInterestManager;checkConsistencyWithBlocks(Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/world/chunk/ChunkSection;)V"))
    private static void onPoiStorageInitForPalette(PointOfInterestManager pointOfInterestStorage, ChunkPos chunkPos, ChunkSection chunkSection) {
        ChunkIoMainThreadTaskUtils.executeMain(() -> pointOfInterestStorage.checkConsistencyWithBlocks(chunkPos, chunkSection));
    }

    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/IChunk;getBlockEntitiesPos()Ljava/util/Set;"))
    private static Set<BlockPos> onChunkGetBlockEntityPositions(IChunk chunk) {
        final AsyncSerializationManager.Scope scope = AsyncSerializationManager.getScope(chunk.getPos());
        return scope != null ? scope.blockEntities.keySet() : chunk.getBlockEntitiesPos();
    }

    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/IChunk;getBlockEntityNbtForSaving(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/nbt/CompoundNBT;"))
    private static CompoundNBT onChunkGetPackedBlockEntityTag(IChunk chunk, BlockPos pos) {
        final AsyncSerializationManager.Scope scope = AsyncSerializationManager.getScope(chunk.getPos());
        if (scope == null) return chunk.getBlockEntityNbtForSaving(pos);
        final TileEntity blockEntity = scope.blockEntities.get(pos);
        if (blockEntity == null || blockEntity.isRemoved()) return null;
        final CompoundNBT compoundTag = new CompoundNBT();
        if (chunk instanceof Chunk) compoundTag.putBoolean("keepPacked", false);
        blockEntity.save(compoundTag);
        return compoundTag;
    }

    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/IChunk;getBlockTicks()Lnet/minecraft/world/ITickList;"))
    private static ITickList<Block> onChunkGetBlockTickScheduler(IChunk chunk) {
        final AsyncSerializationManager.Scope scope = AsyncSerializationManager.getScope(chunk.getPos());
        return scope != null ? scope.blockTickScheduler : chunk.getBlockTicks();
    }

    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/IChunk;getLiquidTicks()Lnet/minecraft/world/ITickList;"))
    private static ITickList<Fluid> onChunkGetFluidTickScheduler(IChunk chunk) {
        final AsyncSerializationManager.Scope scope = AsyncSerializationManager.getScope(chunk.getPos());
        return scope != null ? scope.fluidTickScheduler : chunk.getLiquidTicks();
    }

    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/server/ServerTickList;save(Lnet/minecraft/util/math/ChunkPos;)Lnet/minecraft/nbt/ListNBT;"))
    private static ListNBT onServerTickSchedulerToTag(@SuppressWarnings("rawtypes") ServerTickList serverTickScheduler, ChunkPos chunkPos) {
        final AsyncSerializationManager.Scope scope = AsyncSerializationManager.getScope(chunkPos);
        return scope != null ? CompletableFuture.supplyAsync(() -> serverTickScheduler.save(chunkPos), serverTickScheduler.level.chunkSource.mainThreadProcessor).join() : serverTickScheduler.save(chunkPos);
    }

    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/lighting/WorldLightManager;getLayerListener(Lnet/minecraft/world/LightType;)Lnet/minecraft/world/lighting/IWorldLightListener;"))
    private static IWorldLightListener onLightingProviderGet(WorldLightManager lightingProvider, LightType lightType) {
        final AsyncSerializationManager.Scope scope = AsyncSerializationManager.getScope(null);
        return scope != null ? scope.lighting.get(lightType) : lightingProvider.getLayerListener(lightType);
    }

}
