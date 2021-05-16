package org.yatopiamc.c2me.common.threading.chunkio;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.fluid.Fluid;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.ITickList;
import net.minecraft.world.LightType;
import net.minecraft.world.SerializableTickList;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.lighting.IWorldLightListener;
import net.minecraft.world.lighting.WorldLightManager;
import net.minecraft.world.server.ServerTickList;
import net.minecraft.world.server.ServerWorld;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yatopiamc.c2me.common.util.DeepCloneable;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AsyncSerializationManager {

    private static final Logger LOGGER = LogManager.getLogger("C2ME Async Serialization Manager");

    private static final ThreadLocal<ArrayDeque<Scope>> scopeHolder = ThreadLocal.withInitial(ArrayDeque::new);

    public static void push(Scope scope) {
        scopeHolder.get().push(scope);
    }

    public static Scope getScope(ChunkPos pos) {
        final Scope scope = scopeHolder.get().peek();
        if (pos == null) return scope;
        if (scope != null) {
            if (scope.pos.equals(pos))
                return scope;
            LOGGER.error("Scope position mismatch! Expected: {} but got {}. This will impact stability. Incompatible mods?", scope.pos, pos, new Throwable());
        }
        return null;
    }

    public static void pop(Scope scope) {
        if (scope != scopeHolder.get().peek()) throw new IllegalArgumentException("Scope mismatch");
        scopeHolder.get().pop();
    }

    public static class Scope {
        public final ChunkPos pos;
        public final Map<LightType, IWorldLightListener> lighting;
        public final ITickList<Block> blockTickScheduler;
        public final ITickList<Fluid> fluidTickScheduler;
        public final Map<BlockPos, TileEntity> blockEntities;
        private final AtomicBoolean isOpen = new AtomicBoolean(false);

        @SuppressWarnings("unchecked")
        public Scope(IChunk chunk, ServerWorld world) {
            this.pos = chunk.getPos();
            this.lighting = Arrays.stream(LightType.values()).map(type -> new CachedLightingView(world.getLightEngine(), chunk.getPos(), type)).collect(Collectors.toMap(CachedLightingView::getLightType, Function.identity()));
            final ITickList<Block> blockTickScheduler = chunk.getBlockTicks();
            if (blockTickScheduler instanceof DeepCloneable cloneable) {
                this.blockTickScheduler = (ITickList<Block>) cloneable.deepClone();
            } else {
                final ServerTickList<Block> worldBlockTickScheduler = world.getBlockTicks();
                this.blockTickScheduler = new SerializableTickList<>(Registry.BLOCK::getKey, worldBlockTickScheduler.fetchTicksInChunk(chunk.getPos(), false, true), world.getGameTime());
            }
            final ITickList<Fluid> fluidTickScheduler = chunk.getLiquidTicks();
            if (fluidTickScheduler instanceof DeepCloneable cloneable) {
                this.fluidTickScheduler = (ITickList<Fluid>) cloneable.deepClone();
            } else {
                final ServerTickList<Fluid> worldFluidTickScheduler = world.getLiquidTicks();
                this.fluidTickScheduler = new SerializableTickList<>(Registry.FLUID::getKey, worldFluidTickScheduler.fetchTicksInChunk(chunk.getPos(), false, true), world.getGameTime());
            }
            this.blockEntities = chunk.getBlockEntitiesPos().stream().map(chunk::getBlockEntity).filter(Objects::nonNull).filter(blockEntity -> !blockEntity.isRemoved()).collect(Collectors.toMap(TileEntity::getBlockPos, Function.identity()));
        }

        public void open() {
            if (!isOpen.compareAndSet(false, true)) throw new IllegalStateException("Cannot use scope twice");
        }

        private static final class CachedLightingView implements IWorldLightListener {

            private static final NibbleArray EMPTY = new NibbleArray();

            private final LightType lightType;
            private final Map<SectionPos, NibbleArray> cachedData = new Object2ObjectOpenHashMap<>();

            CachedLightingView(WorldLightManager provider, ChunkPos pos, LightType type) {
                this.lightType = type;
                for (int i = -1; i < 17; i++) {
                    final SectionPos sectionPos = SectionPos.of(pos, i);
                    NibbleArray lighting = provider.getLayerListener(type).getDataLayerData(sectionPos);
                    cachedData.put(sectionPos, lighting != null ? lighting.copy() : null);
                }
            }

            public LightType getLightType() {
                return this.lightType;
            }

            @Override
            public void updateSectionStatus(@Nonnull SectionPos pos, boolean notReady) {
                throw new UnsupportedOperationException();
            }

            @Nonnull
            @Override
            public NibbleArray getDataLayerData(SectionPos pos) {
                return cachedData.getOrDefault(pos, EMPTY);
            }

            @Override
            public int getLightValue(BlockPos pos) {
                throw new UnsupportedOperationException();
            }
        }
    }

}
