package org.yatopiamc.c2me.mixin.threading.chunkio;

import net.minecraft.util.concurrent.ThreadTaskExecutor;
import net.minecraft.world.server.ServerChunkProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerChunkProvider.ChunkExecutor.class)
public abstract class MixinServerChunkManagerMainThreadExecutor extends ThreadTaskExecutor<Runnable> {

    protected MixinServerChunkManagerMainThreadExecutor(String name) {
        super(name);
    }

    @Inject(method = "pollTask", at = @At("RETURN"))
    private void onPostRunTask(CallbackInfoReturnable<Boolean> cir) {
        super.pollTask();
    }

}
