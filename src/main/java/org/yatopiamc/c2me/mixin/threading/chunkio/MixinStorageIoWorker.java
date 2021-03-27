package org.yatopiamc.c2me.mixin.threading.chunkio;

import net.minecraft.world.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.yatopiamc.c2me.common.threading.chunkio.C2MECachedRegionStorage;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(IOWorker.class)
public abstract class MixinStorageIoWorker {

    @Shadow @Final private AtomicBoolean shutdownRequested;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onPostInit(CallbackInfo info) {
        //noinspection ConstantConditions
        if (((Object) this) instanceof C2MECachedRegionStorage) {
            this.shutdownRequested.set(true);
        }
    }

}
