package com.fileindex.controller;

import com.fileindex.dto.AddRootRequest;
import com.fileindex.dto.IndexRootDto;
import com.fileindex.exception.NotFoundException;
import com.fileindex.indexroot.IndexRoot;
import com.fileindex.indexroot.IndexRootStore;
import com.fileindex.service.FileIndexingService;
import com.fileindex.service.FileWatchService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roots")
@RequiredArgsConstructor
public class IndexController {

    private final IndexRootStore store;
    private final FileIndexingService fileIndexingService;
    private final FileWatchService fileWatchService;

    @PostMapping
    public ResponseEntity<IndexRootDto> addRoot(@Valid @RequestBody AddRootRequest request) {
        Path path = Path.of(request.path()).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Путь не существует или не является директорией: " + path);
        }
        if (store.existsForPath(path)) {
            throw new IllegalArgumentException("Путь уже отслеживается: " + path);
        }

        IndexRoot root = store.create(path);
        try {
            fileWatchService.watchRoot(root);
        } catch (IOException e) {
            store.remove(root.getId());
            throw new IllegalArgumentException("Не удалось начать отслеживание пути: " + path + " (" + e.getMessage() + ")");
        }
        fileIndexingService.scanRootAsync(root);

        return ResponseEntity.ok(toDto(root));
    }

    @GetMapping
    public List<IndexRootDto> listRoots() {
        return store.findAll().stream().map(this::toDto).toList();
    }

    @GetMapping("/{id}")
    public IndexRootDto getRoot(@PathVariable String id) {
        return toDto(findOrThrow(id));
    }

    @PostMapping("/{id}/reindex")
    public ResponseEntity<IndexRootDto> reindex(@PathVariable String id) {
        IndexRoot root = findOrThrow(id);
        fileIndexingService.scanRootAsync(root);
        return ResponseEntity.accepted().body(toDto(root));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeRoot(@PathVariable String id) {
        findOrThrow(id);
        fileWatchService.unwatchRoot(id);
        fileIndexingService.deleteByRoot(id);
        store.remove(id);
        return ResponseEntity.noContent().build();
    }

    private IndexRoot findOrThrow(String id) {
        return store.find(id).orElseThrow(() -> new NotFoundException("Директория не найдена: " + id));
    }

    private IndexRootDto toDto(IndexRoot root) {
        long docCount = fileIndexingService.countByRoot(root.getId());
        return new IndexRootDto(
            root.getId(),
            root.getPath().toString(),
            root.getStatus(),
            root.getTotalFiles(),
            root.getProcessedFiles(),
            docCount,
            root.getLastError(),
            root.getCreatedAt()
        );
    }
}
