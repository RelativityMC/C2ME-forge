package org.yatopiamc.c2me.mixin.threading.chunkio;

import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.storage.IOWorker;
import net.minecraft.world.chunk.storage.RegionSectionCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.yatopiamc.c2me.common.threading.chunkio.C2MECachedRegionStorage;
import org.yatopiamc.c2me.common.threading.chunkio.ISerializingRegionBasedStorage;

import javax.annotation.Nullable;
import java.io.File;

@Mixin(RegionSectionCache.class)
public abstract class MixinSerializingRegionBasedStorage implements ISerializingRegionBasedStorage {

    @Shadow protected abstract <T> void readColumn(ChunkPos p_235992_1_, DynamicOps<T> p_235992_2_, @Nullable T p_235992_3_);

    @Override
    public void update(ChunkPos pos, CompoundNBT tag) {
        this.readColumn(pos, NBTDynamicOps.INSTANCE, tag);
    }

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/world/chunk/storage/IOWorker"))
    private IOWorker onStorageIoInit(File file, boolean bl, String string) {
        return new C2MECachedRegionStorage(file, bl, string);
    }
}
