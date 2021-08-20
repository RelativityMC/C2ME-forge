package com.ishland.c2me.mixin.access;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkTicketManager.class)
public interface IChunkTicketManager {

    @Invoker
    void invokeSetWatchDistance(int viewDistance);

    @Accessor
    Long2ObjectMap<ObjectSet<ServerPlayerEntity>> getPlayersByChunkPos();

}
