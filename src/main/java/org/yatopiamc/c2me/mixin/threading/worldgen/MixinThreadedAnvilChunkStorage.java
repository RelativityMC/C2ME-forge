package org.yatopiamc.c2me.mixin.threading.worldgen;

import com.mojang.datafixers.util.Either;
import net.minecraft.util.concurrent.ThreadTaskExecutor;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.server.ChunkHolder;
import net.minecraft.world.server.ChunkManager;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.yatopiamc.c2me.common.threading.GlobalExecutors;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

@Mixin(ChunkManager.class)
public class MixinThreadedAnvilChunkStorage {

    @Shadow @Final private ServerWorld level;
    @Shadow @Final private ThreadTaskExecutor<Runnable> mainThreadExecutor;

    private final Executor mainInvokingExecutor = runnable -> {
        if (this.level.getServer().isSameThread()) {
            runnable.run();
        } else {
            this.mainThreadExecutor.execute(runnable);
        }
    };

    private final ThreadLocal<ChunkStatus> capturedRequiredStatus = new ThreadLocal<>();

    @Inject(method = "scheduleChunkGeneration", at = @At("HEAD"))
    private void onUpgradeChunk(ChunkHolder holder, ChunkStatus requiredStatus, CallbackInfoReturnable<CompletableFuture<Either<Chunk, ChunkHolder.IChunkLoadingError>>> cir) {
        capturedRequiredStatus.set(requiredStatus);
    }

    @Redirect(method = "getEntityTickingRangeFuture", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenApplyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private <U, T> CompletableFuture<U> redirectMainThreadExecutor1(CompletableFuture<T> completableFuture, Function<? super T, ? extends U> fn, Executor executor) {
        return completableFuture.thenApplyAsync(fn, this.mainInvokingExecutor);
    }

    @Redirect(method = "schedule", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenComposeAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private <T, U> CompletableFuture<U> redirectMainThreadExecutor2(CompletableFuture<T> completableFuture, Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return completableFuture.thenComposeAsync(fn, this.mainInvokingExecutor);
    }

    /**
     * @author ishland
     * @reason move to scheduler & improve chunk status transition speed
     */
    @SuppressWarnings("OverwriteTarget")
    @Dynamic
    @Overwrite(aliases = "func_219216_e_")
    private void lambda$scheduleChunkGeneration$21(ChunkHolder chunkHolder, Runnable runnable) { // synthetic method for worldGenExecutor scheduling in upgradeChunk
        final ChunkStatus capturedStatus = capturedRequiredStatus.get();
        capturedRequiredStatus.remove();
        if (capturedStatus != null) {
            final IChunk currentChunk = chunkHolder.getLastAvailable();
            if (currentChunk != null && currentChunk.getStatus().isOrAfter(capturedStatus)) {
                this.mainInvokingExecutor.execute(runnable);
                return;
            }
        }
        GlobalExecutors.scheduler.execute(runnable);
    }

}
