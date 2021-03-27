package org.yatopiamc.c2me.common.threading.chunkio;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import com.ibm.asyncutil.locks.AsyncLock;
import com.ibm.asyncutil.locks.AsyncNamedLock;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.storage.IOWorker;
import net.minecraft.world.chunk.storage.RegionFile;
import net.minecraft.world.chunk.storage.RegionFileCache;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yatopiamc.c2me.common.config.C2MEConfig;
import org.yatopiamc.c2me.common.threading.GlobalExecutors;
import org.yatopiamc.c2me.common.util.C2MEForkJoinWorkerThreadFactory;
import org.yatopiamc.c2me.common.util.SneakyThrow;

import javax.annotation.Nullable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class C2MECachedRegionStorage extends IOWorker {

    private static final CompoundNBT EMPTY_VALUE = new CompoundNBT();
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ForkJoinPool IOExecutor = new ForkJoinPool(
            C2MEConfig.asyncIoConfig.ioWorkerParallelism,
            new C2MEForkJoinWorkerThreadFactory("C2ME chunkio io worker #%d", Thread.NORM_PRIORITY - 3),
            null, true
    );

    private final RegionFileCache storage;
    private final Cache<ChunkPos, CompoundNBT> chunkCache;
    private final ConcurrentHashMap<ChunkPos, CompletableFuture<Void>> writeFutures = new ConcurrentHashMap<>();
    private final AsyncNamedLock<ChunkPos> chunkLocks = AsyncNamedLock.createFair();
    private final AsyncNamedLock<RegionPos> regionLocks = AsyncNamedLock.createFair();
    private final AsyncLock storageLock = AsyncLock.createFair();

    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    public C2MECachedRegionStorage(File file, boolean bl, String string) {
        super(file, bl, string);
        this.storage = new RegionFileCache(file, bl);
        this.chunkCache = CacheBuilder.newBuilder()
                .concurrencyLevel(Runtime.getRuntime().availableProcessors() * 2)
                .expireAfterAccess(3, TimeUnit.SECONDS)
                .maximumSize(8192)
                .removalListener((RemovalNotification<ChunkPos, CompoundNBT> notification) -> {
                    if (notification.getValue() != EMPTY_VALUE)
                        scheduleWrite(notification.getKey(), notification.getValue());
                })
                .build();
        this.tick();
    }

    private void tick() {
        long startTime = System.currentTimeMillis();
        chunkCache.cleanUp();
        if (!isClosed.get())
            GlobalExecutors.scheduler.schedule(this::tick, 1000 - (System.currentTimeMillis() - startTime), TimeUnit.MILLISECONDS);
    }

    private CompletableFuture<RegionFile> getRegionFile(ChunkPos pos) {
        return storageLock.acquireLock().toCompletableFuture().thenApplyAsync(lockToken -> {
            try {
                return storage.getRegionFile(pos);
            } catch (IOException e) {
                SneakyThrow.sneaky(e);
                throw new RuntimeException(e);
            } finally {
                lockToken.releaseLock();
            }
        }, GlobalExecutors.scheduler);
    }

    private void scheduleWrite(ChunkPos pos, CompoundNBT chunkData) {
        writeFutures.put(pos, regionLocks.acquireLock(new RegionPos(pos)).toCompletableFuture().thenCombineAsync(getRegionFile(pos), (lockToken, regionFile) -> {
            try (final DataOutputStream dataOutputStream = regionFile.getChunkDataOutputStream(pos)) {
                CompressedStreamTools.write(chunkData, dataOutputStream);
            } catch (Throwable t) {
                LOGGER.error("Failed to store chunk {}", pos, t);
            } finally {
                lockToken.releaseLock();
            }
            return null;
        }, IOExecutor).exceptionally(t -> null).thenAccept(unused -> {
        }));
    }

    @Override
    public CompletableFuture<Void> store(ChunkPos pos, CompoundNBT nbt) {
        ensureOpen();
        Preconditions.checkNotNull(pos);
        Preconditions.checkNotNull(nbt);
        return chunkLocks.acquireLock(pos).toCompletableFuture().thenAcceptAsync(lockToken -> {
            try {
                this.chunkCache.put(pos, nbt);
            } finally {
                lockToken.releaseLock();
            }
        }, GlobalExecutors.scheduler);
    }

    public CompletableFuture<CompoundNBT> getNbtAtAsync(ChunkPos pos) {
        ensureOpen();
        // Check cache
        {
            final CompoundNBT cachedValue = this.chunkCache.getIfPresent(pos);
            if (cachedValue != null) {
                if (cachedValue == EMPTY_VALUE)
                    return CompletableFuture.completedFuture(null);
                else
                    return CompletableFuture.completedFuture(cachedValue);
            }
        }
        return chunkLocks.acquireLock(pos).toCompletableFuture().thenComposeAsync(lockToken -> {
            try {
                // Check again in single-threaded environment
                final CompoundNBT cachedValue = this.chunkCache.getIfPresent(pos);
                if (cachedValue != null) {
                    if (cachedValue == EMPTY_VALUE)
                        return CompletableFuture.completedFuture(null);
                    else
                        return CompletableFuture.completedFuture(cachedValue);
                }
                return regionLocks.acquireLock(new RegionPos(pos)).thenCombineAsync(getRegionFile(pos), (lockToken1, regionFile) -> {
                    try {
                        final CompoundNBT queriedTag;
                        try (final DataInputStream dataInputStream = regionFile.getChunkDataInputStream(pos)) {
                            if (dataInputStream != null)
                                queriedTag = CompressedStreamTools.read(dataInputStream);
                            else
                                queriedTag = null;
                        }
                        chunkLocks.acquireLock(pos).thenAccept(lockToken2 -> {
                            try {
                                chunkCache.put(pos, queriedTag != null ? queriedTag : EMPTY_VALUE);
                            } finally {
                                lockToken2.releaseLock();
                            }
                        });
                        return queriedTag;
                    } catch (Throwable t) {
                        LOGGER.warn("Failed to read chunk {}", pos, t);
                        return null;
                    } finally {
                        lockToken1.releaseLock();
                    }
                }, IOExecutor);
            } catch (Throwable t) {
                LOGGER.warn("Failed to read chunk {}", pos, t);
                return CompletableFuture.completedFuture(null);
            } finally {
                lockToken.releaseLock();
            }
        }, GlobalExecutors.scheduler);
    }

    @Nullable
    @Override
    public CompoundNBT load(ChunkPos pos) {
        return getNbtAtAsync(pos).join();
    }

    @Override
    public CompletableFuture<Void> synchronize() {
        chunkCache.invalidateAll();
        return CompletableFuture.allOf(writeFutures.values().toArray(CompletableFuture[]::new)).thenRunAsync(() -> {
            try {
                storage.flush();
            } catch (IOException e) {
                LOGGER.warn("Failed to synchronized chunks", e);
            }
        });
    }

    @Override
    public void close() throws IOException {
        if (this.isClosed.compareAndSet(false, true)) {
            synchronize().join();
            this.storage.close();
        }
    }

    private void ensureOpen() {
        Preconditions.checkState(!isClosed.get(), "Tried to modify a closed instance");
    }

    private static class RegionPos {
        private final int x;
        private final int z;

        @SuppressWarnings("unused")
        private RegionPos(int x, int z) {
            this.x = x;
            this.z = z;
        }

        private RegionPos(ChunkPos chunkPos) {
            this.x = chunkPos.getRegionX();
            this.z = chunkPos.getRegionZ();
        }

        @SuppressWarnings("unused")
        public int getX() {
            return x;
        }

        @SuppressWarnings("unused")
        public int getZ() {
            return z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RegionPos regionPos = (RegionPos) o;
            return x == regionPos.x && z == regionPos.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, z);
        }

        @Override
        public String toString() {
            return "RegionPos{" +
                    "x=" + x +
                    ", z=" + z +
                    '}';
        }
    }
}
