package com.fileindex.service;

import com.fileindex.config.IndexingProperties;
import com.fileindex.indexroot.IndexRoot;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Keeps the index in sync with the filesystem in real time using java.nio.file.WatchService.
 * A single watcher instance backs all tracked roots; watch keys are tagged with the root
 * they belong to so a root can be un-watched independently of the others.
 *
 * Rapid-fire events for the same path (e.g. an editor writing a temp file then renaming it)
 * are debounced per-path before triggering a re-extract/index, so a burst of OS events only
 * causes one Tika extraction + one ES write.
 */
@Service
@RequiredArgsConstructor
public class FileWatchService {

    private static final Logger log = LoggerFactory.getLogger(FileWatchService.class);

    private final FileIndexingService fileIndexingService;
    private final IndexingProperties properties;

    private final Map<WatchKey, WatchedDir> watchedDirs = new ConcurrentHashMap<>();
    private final Map<Path, ScheduledFuture<?>> pendingEvents = new ConcurrentHashMap<>();

    private WatchService watchService;
    private ExecutorService watchLoopExecutor;
    private ScheduledExecutorService debounceExecutor;
    private volatile boolean running;

    private record WatchedDir(String rootId, Path dir) {
    }

    public synchronized void start() throws IOException {
        if (watchService != null) {
            return;
        }
        watchService = FileSystems.getDefault().newWatchService();
        watchLoopExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "file-watch-loop");
            t.setDaemon(true);
            return t;
        });
        debounceExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "file-watch-debounce");
            t.setDaemon(true);
            return t;
        });
        running = true;
        watchLoopExecutor.submit(this::runLoop);
    }

    public void watchRoot(IndexRoot root) throws IOException {
        start();
        registerRecursively(root.getId(), root.getPath());
    }

    public void unwatchRoot(String rootId) {
        watchedDirs.entrySet().removeIf(entry -> {
            if (entry.getValue().rootId().equals(rootId)) {
                entry.getKey().cancel();
                return true;
            }
            return false;
        });
    }

    private void registerRecursively(String rootId, Path dir) throws IOException {
        if (!Files.isDirectory(dir) || fileIndexingService.isExcluded(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path candidate : (Iterable<Path>) walk.filter(Files::isDirectory)::iterator) {
                if (fileIndexingService.isExcluded(candidate)) {
                    continue;
                }
                WatchKey key = candidate.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
                );
                watchedDirs.put(key, new WatchedDir(rootId, candidate));
            }
        }
    }

    private void runLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (java.nio.file.ClosedWatchServiceException e) {
                return;
            }

            WatchedDir watchedDir = watchedDirs.get(key);
            if (watchedDir == null) {
                key.reset();
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }
                Path name = (Path) event.context();
                Path child = watchedDir.dir().resolve(name);

                if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(child)) {
                    handleNewDirectory(watchedDir.rootId(), child);
                } else {
                    scheduleReindex(watchedDir.rootId(), child);
                }
            }

            boolean valid = key.reset();
            if (!valid) {
                watchedDirs.remove(key);
            }
        }
    }

    private void handleNewDirectory(String rootId, Path dir) {
        try {
            registerRecursively(rootId, dir);
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile).forEach(f -> scheduleReindex(rootId, f));
            }
        } catch (IOException e) {
            log.warn("Failed to register new directory {} ({})", dir, e.getMessage());
        }
    }

    private void scheduleReindex(String rootId, Path path) {
        pendingEvents.compute(path, (p, existing) -> {
            if (existing != null) {
                existing.cancel(false);
            }
            return debounceExecutor.schedule(
                () -> handleDebouncedEvent(rootId, path),
                properties.getWatchDebounceMs(),
                TimeUnit.MILLISECONDS
            );
        });
    }

    private void handleDebouncedEvent(String rootId, Path path) {
        pendingEvents.remove(path);
        try {
            if (Files.exists(path) && Files.isRegularFile(path)) {
                fileIndexingService.indexSingleFile(rootId, path);
            } else if (!Files.exists(path)) {
                fileIndexingService.deleteFile(path);
            }
        } catch (Exception e) {
            log.warn("Failed to process watch event for {} ({})", path, e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
                // best-effort shutdown
            }
        }
        if (watchLoopExecutor != null) {
            watchLoopExecutor.shutdownNow();
        }
        if (debounceExecutor != null) {
            debounceExecutor.shutdownNow();
        }
    }
}
