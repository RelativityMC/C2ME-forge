package org.yatopiamc.c2me.mixin.util.chunkgenerator;

import net.minecraft.command.CommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.server.command.ChunkGenWorker;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.yatopiamc.c2me.common.threading.worldgen.C2MEChunkGenWorker;

@Mixin(targets = "net.minecraftforge.server.command.CommandGenerate")
public class MixinCommandGenerate {

    @Redirect(method = "execute", at = @At(value = "NEW", target = "net/minecraftforge/server/command/ChunkGenWorker"), remap = false)
    private static ChunkGenWorker redirectChunkGenWorkerInit(CommandSource listener, BlockPos start, int total, ServerWorld dim, int interval) {
        return new C2MEChunkGenWorker(listener, start, total, dim, interval);
    }

}
