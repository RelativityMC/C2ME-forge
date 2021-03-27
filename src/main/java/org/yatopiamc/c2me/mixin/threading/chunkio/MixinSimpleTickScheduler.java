package org.yatopiamc.c2me.mixin.threading.chunkio;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.ITickList;
import net.minecraft.world.SerializableTickList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.yatopiamc.c2me.common.util.DeepCloneable;

import java.util.function.Function;

@Mixin(SerializableTickList.class)
public abstract class MixinSimpleTickScheduler<T> implements DeepCloneable {

    @Shadow @Final private Function<T, ResourceLocation> toId;

    @Shadow public abstract void copyOut(ITickList<T> scheduler);

    @Override
    public Object deepClone() {
        final SerializableTickList<T> scheduler = new SerializableTickList<>(toId, new ObjectArrayList<>());
        copyOut(scheduler);
        return scheduler;
    }
}
