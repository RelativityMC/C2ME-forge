package org.yatopiamc.c2me.mixin.threading.worldgen;

import net.minecraft.world.server.ChunkHolder;
import net.minecraft.world.server.ChunkManager;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.yatopiamc.c2me.common.threading.GlobalExecutors;

@Mixin(ChunkManager.class)
public class MixinThreadedAnvilChunkStorage {

    /**
     * @author ishland
     * @reason move to scheduler
     */
    @SuppressWarnings("OverwriteTarget")
    @Dynamic
    @Overwrite(aliases = "func_219216_e_")
    private void lambda$scheduleChunkGeneration$21(ChunkHolder chunkHolder, Runnable runnable) { // synthetic method for worldGenExecutor scheduling
        GlobalExecutors.scheduler.execute(runnable);
    }

}
