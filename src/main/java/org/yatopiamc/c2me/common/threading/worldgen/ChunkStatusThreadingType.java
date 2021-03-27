package org.yatopiamc.c2me.common.threading.worldgen;

import com.google.common.base.Preconditions;
import com.ibm.asyncutil.locks.AsyncLock;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

public enum ChunkStatusThreadingType {

    PARALLELIZED() {
        @Override
        public <T> CompletableFuture<T> runTask(AsyncLock lock, Supplier<CompletableFuture<T>> completableFuture) {
            return CompletableFuture.supplyAsync(completableFuture, WorldGenThreadingExecutorUtils.mainExecutor).thenCompose(Function.identity());
        }
    },
    SINGLE_THREADED() {
        @Override
        public <T> CompletableFuture<T> runTask(AsyncLock lock, Supplier<CompletableFuture<T>> completableFuture) {
            Preconditions.checkNotNull(lock);
            return lock.acquireLock().toCompletableFuture().thenComposeAsync(lockToken -> {
                try {
                    return completableFuture.get();
                } finally {
                    lockToken.releaseLock();
                }
            }, WorldGenThreadingExecutorUtils.mainExecutor);
        }
    },
    AS_IS() {
        @Override
        public <T> CompletableFuture<T> runTask(AsyncLock lock, Supplier<CompletableFuture<T>> completableFuture) {
            return completableFuture.get();
        }
    };

    public abstract <T> CompletableFuture<T> runTask(AsyncLock lock, Supplier<CompletableFuture<T>> completableFuture);

}
