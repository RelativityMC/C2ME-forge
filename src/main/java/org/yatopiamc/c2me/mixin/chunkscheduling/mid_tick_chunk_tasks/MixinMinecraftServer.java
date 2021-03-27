package org.yatopiamc.c2me.mixin.chunkscheduling.mid_tick_chunk_tasks;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.yatopiamc.c2me.common.chunkscheduling.ServerMidTickTask;
import org.yatopiamc.c2me.mixin.util.accessor.IThreadTaskExecutor;

import java.util.concurrent.atomic.AtomicLong;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer implements ServerMidTickTask {

    @Shadow public abstract Iterable<ServerWorld> getAllLevels();

    @Shadow @Final private Thread serverThread;
    private static final long minMidTickTaskInterval = 25_000L; // 25us
    private final AtomicLong lastRun = new AtomicLong(System.nanoTime());

    public void executeTasksMidTick() {
        if (this.serverThread != Thread.currentThread()) return;
        if (System.nanoTime() - lastRun.get() < minMidTickTaskInterval) return;
        for (ServerWorld world : this.getAllLevels()) {
            ((IThreadTaskExecutor) world.getChunkSource().mainThreadProcessor).IPollTask();
        }
        lastRun.set(System.nanoTime());
    }

}
