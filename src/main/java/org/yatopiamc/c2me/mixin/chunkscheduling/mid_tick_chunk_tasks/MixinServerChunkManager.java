package org.yatopiamc.c2me.mixin.chunkscheduling.mid_tick_chunk_tasks;

import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.yatopiamc.c2me.common.chunkscheduling.ServerMidTickTask;

@Mixin(ServerChunkProvider.class)
public class MixinServerChunkManager {

    @Shadow @Final private ServerWorld level;

    @Dynamic
    @Inject(method = "lambda$tickChunks$5", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/server/ServerWorld;tickChunk(Lnet/minecraft/world/chunk/Chunk;I)V"))
    private void onPostTickChunk(CallbackInfo ci) { // TODO synthetic method - in tickChunks()
        ((ServerMidTickTask) this.level.getServer()).executeTasksMidTick();
    }

}
