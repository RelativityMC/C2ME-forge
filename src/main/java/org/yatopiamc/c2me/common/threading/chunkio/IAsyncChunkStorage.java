package org.yatopiamc.c2me.common.threading.chunkio;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.ChunkPos;

import java.util.concurrent.CompletableFuture;

public interface IAsyncChunkStorage {

    CompletableFuture<CompoundNBT> getNbtAtAsync(ChunkPos pos);

}
