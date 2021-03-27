package org.yatopiamc.c2me.mixin.threading.chunkio;

import net.minecraft.nbt.ListNBT;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkPrimerTickList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.yatopiamc.c2me.common.util.DeepCloneable;

import java.util.function.Predicate;

@Mixin(ChunkPrimerTickList.class)
public abstract class MixinChunkTickScheduler<T> implements DeepCloneable {

    @Shadow
    public abstract ListNBT save();

    @Shadow
    @Final
    private ChunkPos chunkPos;
    @Shadow @Final protected Predicate<T> ignore;

    public ChunkPrimerTickList<T> deepClone() {
        return new ChunkPrimerTickList<>(ignore, chunkPos, save());
    }
}
