package org.yatopiamc.c2me.mixin.threading.lighting;

import net.minecraft.world.server.ServerWorldLightManager;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorldLightManager.class)
public abstract class MixinServerLightingProvider {

    @Shadow public abstract void tryScheduleUpdate();

    @Dynamic
    @Inject(method = "lambda$tryScheduleUpdate$22", at = @At("RETURN"))
    private void onPostRunTask(CallbackInfo info) {
        this.tryScheduleUpdate(); // Run more tasks
    }

}
