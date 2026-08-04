package com.fileindex.service;

import com.fileindex.config.IndexingProperties;
import com.fileindex.indexroot.IndexRoot;
import com.fileindex.indexroot.IndexRootStatus;
import com.fileindex.model.IndexedFileDocument;
import com.fileindex.repository.IndexedFileRepository;
import com.fileindex.util.PathHashUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Walks a root directory, extracts text from each eligible file and bulk-indexes it.
 * Also handles single-file upsert/delete for the live watcher, and root-scoped cleanup.
 */
@Service
@RequiredArgsConstructor
public class FileIndexingService {

    private static final Logger log = LoggerFactory.getLogger(FileIndexingService.class);

    private final IndexedFileRepository repository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final TextExtractionService textExtractionService;
    private final IndexingProperties properties;

    @Async("indexingExecutor")
    public void scanRootAsync(IndexRoot root) {
        scanRoot(root);
    }

    public void scanRoot(IndexRoot root) {
        root.setStatus(IndexRootStatus.SCANNING);
        root.setLastError(null);
        try {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(root.getPath())) {
                files = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> !isExcluded(p))
                    .filter(this::withinSizeLimit)
                    .toList();
            }
            root.resetProgress(files.size());

            List<IndexedFileDocument> batch = new ArrayList<>(properties.getBatchSize());
            for (Path file : files) {
                try {
                    batch.add(buildDocument(root.getId(), file));
                } catch (IOException e) {
                    log.warn("Skipping {} ({})", file, e.getMessage());
                }
                root.incrementProcessed();
                if (batch.size() >= properties.getBatchSize()) {
                    repository.saveAll(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                repository.saveAll(batch);
            }
            root.setStatus(IndexRootStatus.WATCHING);
        } catch (IOException e) {
            log.error("Scan failed for root {}", root.getPath(), e);
            root.setLastError(e.getMessage());
            root.setStatus(IndexRootStatus.ERROR);
        }
    }

    public void indexSingleFile(String rootId, Path file) {
        if (!Files.isRegularFile(file) || isExcluded(file) || !withinSizeLimit(file)) {
            return;
        }
        try {
            repository.save(buildDocument(rootId, file));
        } catch (IOException e) {
            log.warn("Failed to index {} ({})", file, e.getMessage());
        }
    }

    public void deleteFile(Path file) {
        repository.deleteById(PathHashUtil.hash(file));
    }

    public void deleteByRoot(String rootId) {
        Query query = new CriteriaQuery(Criteria.where("rootId").is(rootId));
        elasticsearchOperations.delete(DeleteQuery.builder(query).build(), IndexedFileDocument.class);
    }

    public long countByRoot(String rootId) {
        Query query = new CriteriaQuery(Criteria.where("rootId").is(rootId));
        return elasticsearchOperations.count(query, IndexedFileDocument.class);
    }

    /** True if any path segment matches a configured excluded directory name (e.g. .git, node_modules). */
    public boolean isExcluded(Path path) {
        for (Path segment : path) {
            if (properties.getExcludedDirs().contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean withinSizeLimit(Path file) {
        try {
            return Files.size(file) <= properties.maxFileSizeBytes();
        } catch (IOException e) {
            return false;
        }
    }

    private IndexedFileDocument buildDocument(String rootId, Path file) throws IOException {
        ExtractedContent extracted = textExtractionService.extract(file);
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        Path absolute = file.toAbsolutePath().normalize();
        String fileName = absolute.getFileName().toString();
        String id = PathHashUtil.hash(absolute);

        // Re-scanning (via the file watcher or a manual reindex) rebuilds this document from
        // scratch and repository.save() replaces it wholesale - without carrying tags forward,
        // every edit to a file on disk would silently wipe out its tags.
        List<String> existingTags = List.of();
        List<String> existingAiTags = List.of();
        Optional<IndexedFileDocument> existing = repository.findById(id);
        if (existing.isPresent()) {
            existingTags = existing.get().getTags();
            existingAiTags = existing.get().getAiTags();
        }

        return IndexedFileDocument.builder()
            .id(id)
            .path(absolute.toString())
            .fileName(fileName)
            .extension(extensionOf(fileName))
            .directory(absolute.getParent() != null ? absolute.getParent().toString() : "")
            .content(extracted.text())
            .contentType(extracted.contentType())
            .sizeBytes(attrs.size())
            .modifiedAt(attrs.lastModifiedTime().toInstant())
            .indexedAt(Instant.now())
            .rootId(rootId)
            .author(extracted.author())
            .documentTitle(extracted.title())
            .documentCreatedAt(extracted.createdDate())
            .tags(new ArrayList<>(existingTags))
            .aiTags(new ArrayList<>(existingAiTags))
            .build();
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot >= 0 && dot < fileName.length() - 1) ? fileName.substring(dot + 1).toLowerCase() : "";
    }
}
