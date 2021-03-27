package org.yatopiamc.c2me.mixin.chunkscheduling.mid_tick_chunk_tasks;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.yatopiamc.c2me.common.chunkscheduling.ServerMidTickTask;

import javax.annotation.Nullable;

@Mixin(World.class)
public abstract class MixinWorld {

    @Shadow @Nullable
    public abstract MinecraftServer getServer();

    @Shadow @Final public boolean isClientSide;

    @Inject(method = "guardEntityTick", at = @At("TAIL"))
    private void onPostTickEntity(CallbackInfo ci) {
        final MinecraftServer server = this.getServer();
        if (!this.isClientSide && server != null) {
            ((ServerMidTickTask) server).executeTasksMidTick();
        }
    }

}
