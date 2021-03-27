package org.yatopiamc.c2me.mixin.util.accessor;

import net.minecraft.util.concurrent.ThreadTaskExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ThreadTaskExecutor.class)
public interface IThreadTaskExecutor {

    @Invoker("pollTask")
    boolean IPollTask();

}
