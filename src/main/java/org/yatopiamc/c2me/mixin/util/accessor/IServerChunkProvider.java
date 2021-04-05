package org.yatopiamc.c2me.mixin.util.accessor;

import net.minecraft.world.server.ChunkHolder;
import net.minecraft.world.server.ServerChunkProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerChunkProvider.class)
public interface IServerChunkProvider {

    @Invoker("getVisibleChunkIfPresent")
    ChunkHolder IGetVisibleChunkIfPresent(long p_219219_1_);

    @Invoker("runDistanceManagerUpdates")
    boolean IRunDistanceManagerUpdates();

}
