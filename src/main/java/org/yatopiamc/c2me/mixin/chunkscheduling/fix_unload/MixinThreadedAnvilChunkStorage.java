package org.yatopiamc.c2me.mixin.chunkscheduling.fix_unload;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.util.concurrent.ThreadTaskExecutor;
import net.minecraft.village.PointOfInterestManager;
import net.minecraft.world.server.ChunkHolder;
import net.minecraft.world.server.ChunkManager;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.yatopiamc.c2me.common.structs.LongHashSet;
import org.yatopiamc.c2me.common.util.ShouldKeepTickingUtils;

import java.util.function.BooleanSupplier;

@Mixin(ChunkManager.class)
public abstract class MixinThreadedAnvilChunkStorage {

    @Shadow @Final private ThreadTaskExecutor<Runnable> mainThreadExecutor;

    @Shadow protected abstract void processUnloads(BooleanSupplier shouldKeepTicking);

    @Mutable
    @Shadow @Final private LongSet toDrop;

    /**
     * @author ishland
     * @reason Queue unload immediately
     */
    @SuppressWarnings("OverwriteTarget")
    @Dynamic
    @Overwrite(aliases = "func_222962_a_")
    private void lambda$unpackTicks$38(ChunkHolder holder, Runnable runnable) { // TODO synthetic method in thenApplyAsync call of makeChunkAccessible
        this.mainThreadExecutor.execute(runnable);
    }

    @Redirect(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/village/PointOfInterestManager;tick(Ljava/util/function/BooleanSupplier;)V"))
    private void redirectTickPointOfInterestStorageTick(PointOfInterestManager pointOfInterestStorage, BooleanSupplier shouldKeepTicking) {
        pointOfInterestStorage.tick(ShouldKeepTickingUtils.minimumTicks(shouldKeepTicking, 32));
    }

    @Redirect(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/server/ChunkManager;processUnloads(Ljava/util/function/BooleanSupplier;)V"))
    private void redirectTickUnloadChunks(ChunkManager threadedAnvilChunkStorage, BooleanSupplier shouldKeepTicking) {
        this.processUnloads(ShouldKeepTickingUtils.minimumTicks(shouldKeepTicking, 32));
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo info) {
        this.toDrop = new LongHashSet();
    }

}
