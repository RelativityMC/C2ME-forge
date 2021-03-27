package org.yatopiamc.c2me.mixin.util.log4j2shutdownhookisnomore;

import net.minecraft.server.dedicated.DedicatedServer;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DedicatedServer.class)
public class MixinMinecraftDedicatedServer {

    @Inject(method = "onServerExit", at = @At("RETURN"))
    private void onPostShutdown(CallbackInfo ci) {
        LogManager.shutdown();
        new Thread(() -> System.exit(0)).start();
    }

}
