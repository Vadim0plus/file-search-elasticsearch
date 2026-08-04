package com.fileindex.config;

import com.fileindex.indexroot.IndexRoot;
import com.fileindex.indexroot.IndexRootStore;
import com.fileindex.service.FileIndexingService;
import com.fileindex.service.FileWatchService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Registers {@code app.indexing.default-root} (e.g. the /data volume mounted by
 * docker-compose) as an index root on startup, mirroring what IndexController#addRoot does
 * for a manually-added one. Without this, a fresh checkout shows an empty index until someone
 * adds a root through the UI or curl, even though sample-data is already sitting on disk.
 */
@Order(2)
@Component
@RequiredArgsConstructor
public class DefaultIndexRootInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultIndexRootInitializer.class);

    private final IndexingProperties properties;
    private final IndexRootStore store;
    private final FileIndexingService fileIndexingService;
    private final FileWatchService fileWatchService;

    @Override
    public void run(ApplicationArguments args) {
        String configured = properties.getDefaultRoot();
        if (configured == null || configured.isBlank()) {
            return;
        }

        Path path = Path.of(configured).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            log.warn("app.indexing.default-root={} is not a directory, skipping auto-registration", path);
            return;
        }
        if (store.existsForPath(path)) {
            return;
        }

        IndexRoot root = store.create(path);
        try {
            fileWatchService.watchRoot(root);
        } catch (IOException e) {
            store.remove(root.getId());
            log.warn("Failed to watch default index root {}: {}", path, e.getMessage());
            return;
        }
        fileIndexingService.scanRootAsync(root);
        log.info("Registered default index root {}", path);
    }
}
