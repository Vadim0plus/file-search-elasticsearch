package com.fileindex.indexroot;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable, thread-safe state for one tracked root directory. Counters are updated
 * concurrently by the scan thread and the watch-service event loop.
 */
public class IndexRoot {

    private final String id;
    private final Path path;
    private final Instant createdAt = Instant.now();
    private final AtomicReference<IndexRootStatus> status = new AtomicReference<>(IndexRootStatus.IDLE);
    private final AtomicInteger totalFiles = new AtomicInteger(0);
    private final AtomicInteger processedFiles = new AtomicInteger(0);
    private final AtomicReference<String> lastError = new AtomicReference<>();

    public IndexRoot(String id, Path path) {
        this.id = id;
        this.path = path;
    }

    public String getId() {
        return id;
    }

    public Path getPath() {
        return path;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public IndexRootStatus getStatus() {
        return status.get();
    }

    public void setStatus(IndexRootStatus newStatus) {
        status.set(newStatus);
    }

    public int getTotalFiles() {
        return totalFiles.get();
    }

    public int getProcessedFiles() {
        return processedFiles.get();
    }

    public void resetProgress(int total) {
        totalFiles.set(total);
        processedFiles.set(0);
    }

    public void incrementProcessed() {
        processedFiles.incrementAndGet();
    }

    public String getLastError() {
        return lastError.get();
    }

    public void setLastError(String error) {
        lastError.set(error);
    }
}
