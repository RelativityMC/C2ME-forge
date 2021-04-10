package org.yatopiamc.c2me.common.threading.worldgen;

import net.minecraft.command.CommandSource;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.server.ChunkHolder;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.server.TicketType;
import net.minecraftforge.server.command.ChunkGenWorker;
import org.yatopiamc.c2me.mixin.util.accessor.IServerChunkProvider;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.Semaphore;

public class C2MEChunkGenWorker extends ChunkGenWorker {

    private static final TicketType<Unit> C2ME_CHUNK_GEN = TicketType.create("c2me_chunk_gen", (o1, o2) -> 0);

    private static final int MAX_WORKING = 2048;

    private final CommandSource listener;
    private final ServerWorld world;
    private final Queue<BlockPos> queue;
    private final Semaphore working = new Semaphore(MAX_WORKING);

    private int genned = 0;
    private int gennedLastSecond = 0;
    private long lastNotification = 0;
    private final int[] gennedSecond = new int[8];
    private int gennedSecondLocation = 0;
    private long lastPollTask = System.nanoTime();

    public C2MEChunkGenWorker(CommandSource listener, BlockPos start, int total, ServerWorld dim, int interval) {
        super(listener, start, total, dim, interval);
        this.listener = listener;
        this.queue = buildQueue();
        this.world = dim;
        Arrays.fill(gennedSecond, -1);
    }

    @Override
    public boolean hasWork() {
        return !queue.isEmpty() || genned < total;
    }

    private double average(int[] arr) {
        int total = 0;
        int emptyCount = 0;
        for (int i : arr) {
            if (i == -1) {
                emptyCount ++;
            } else {
                total += i;
            }
        }
        return total / ((double) (arr.length - emptyCount));
    }

    @Override
    public boolean doWork() {
        final long timeMillis = System.currentTimeMillis();
        final long timeNanos = System.nanoTime();
        if (timeNanos - lastPollTask > 10_000) {
            world.getChunkSource().pollTask();
            lastPollTask = timeNanos;
        }
        if (timeMillis - lastNotification > 1000) {
            gennedSecond[(gennedSecondLocation ++ % gennedSecond.length)] = gennedLastSecond;
            listener.sendSuccess(new StringTextComponent(String.format("Running generation task for %s: %d / %d (%.1f%%) at %.1f (%d) cps",
                    world, genned, total, genned / ((double) total) * 100.0, average(gennedSecond), gennedLastSecond)), true);
            lastNotification = timeMillis;
            gennedLastSecond = 0;
        }

        if (working.tryAcquire()) {
            final BlockPos pos = queue.poll();
            if (pos == null) {
                working.release();
            } else {
                final ChunkPos chunkPos = new ChunkPos(pos.getX(), pos.getZ());
                world.getChunkSource().registerTickingTicket(C2ME_CHUNK_GEN, chunkPos, 0, Unit.INSTANCE);
                ((IServerChunkProvider) world.getChunkSource()).IRunDistanceManagerUpdates();
                final ChunkHolder chunkHolder = ((IServerChunkProvider) world.getChunkSource()).IGetVisibleChunkIfPresent(chunkPos.toLong());
                if (chunkHolder == null) {
                    listener.sendSuccess(new StringTextComponent("Null chunkholder for " + chunkPos), true);
                    world.getChunkSource().releaseTickingTicket(C2ME_CHUNK_GEN, chunkPos, 0, Unit.INSTANCE);
                    working.release();
                    gennedLastSecond ++;
                    genned ++;
                } else {
                    world.getChunkSource().chunkMap.schedule(chunkHolder, ChunkStatus.FULL).thenRunAsync(() -> {
                        world.getChunkSource().releaseTickingTicket(C2ME_CHUNK_GEN, chunkPos, 0, Unit.INSTANCE);
                        working.release();
                        gennedLastSecond ++;
                        genned ++;
                    }, world.chunkSource.mainThreadProcessor);
                }
            }
        }
        if (!hasWork()) {
            listener.sendSuccess(new TranslationTextComponent("commands.forge.gen.complete", total, total, world.dimension().location()), true);
            return false;
        }
        return true;
    }
}
