package org.yatopiamc.c2me.common.threading.chunkio;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.ChunkPos;

public interface ISerializingRegionBasedStorage {

    void update(ChunkPos pos, CompoundNBT tag);

}
