package org.yatopiamc.c2me.mixin.threading.chunkio;

import com.ibm.asyncutil.locks.AsyncLock;
import com.mojang.datafixers.DataFixer;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.SharedConstants;
import net.minecraft.util.datafix.DefaultTypeReferences;
import net.minecraft.world.World;
import net.minecraft.world.chunk.storage.ChunkLoader;
import net.minecraft.world.chunk.storage.IOWorker;
import net.minecraft.world.gen.feature.structure.LegacyStructureDataUtil;
import net.minecraft.world.storage.DimensionSavedDataManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.yatopiamc.c2me.common.threading.chunkio.C2MECachedRegionStorage;

import javax.annotation.Nullable;
import java.io.File;
import java.util.function.Supplier;

@Mixin(ChunkLoader.class)
public abstract class MixinVersionedChunkStorage {

    @Shadow @Final protected DataFixer fixerUpper;

    @Shadow @Nullable
    private LegacyStructureDataUtil legacyStructureHandler;

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/world/chunk/storage/IOWorker"))
    private IOWorker onStorageIoInit(File file, boolean bl, String string) {
        return new C2MECachedRegionStorage(file, bl, string);
    }

    private AsyncLock featureUpdaterLock = AsyncLock.createFair();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo info) {
        this.featureUpdaterLock = AsyncLock.createFair();
    }

    /**
     * @author ishland
     * @reason async loading
     */
    @Overwrite
    public CompoundNBT upgradeChunkTag(RegistryKey<World> registryKey, Supplier<DimensionSavedDataManager> persistentStateManagerFactory, CompoundNBT tag) {
        // TODO [VanillaCopy] - check when updating minecraft version
        int i = ChunkLoader.getVersion(tag);
        if (i < 1493) {
            try (final AsyncLock.LockToken ignored = featureUpdaterLock.acquireLock().toCompletableFuture().join()) { // C2ME - async chunk loading
                tag = NBTUtil.update(this.fixerUpper, DefaultTypeReferences.CHUNK, tag, i, 1493);
                if (tag.getCompound("Level").getBoolean("hasLegacyStructureData")) {
                    if (this.legacyStructureHandler == null) {
                        this.legacyStructureHandler = LegacyStructureDataUtil.getLegacyStructureHandler(registryKey, persistentStateManagerFactory.get());
                    }

                    tag = this.legacyStructureHandler.updateFromLegacy(tag);
                }
            } // C2ME - async chunk loading
        }

        tag = NBTUtil.update(this.fixerUpper, DefaultTypeReferences.CHUNK, tag, Math.max(1493, i));
        if (i < SharedConstants.getCurrentVersion().getWorldVersion()) {
            tag.putInt("DataVersion", SharedConstants.getCurrentVersion().getWorldVersion());
        }

        return tag;
    }

    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/feature/structure/LegacyStructureDataUtil;removeIndex(J)V"))
    private void onSetTagAtFeatureUpdaterMarkResolved(LegacyStructureDataUtil featureUpdater, long l) {
        try (final AsyncLock.LockToken ignored = featureUpdaterLock.acquireLock().toCompletableFuture().join()) {
            featureUpdater.removeIndex(l);
        }
    }

}
