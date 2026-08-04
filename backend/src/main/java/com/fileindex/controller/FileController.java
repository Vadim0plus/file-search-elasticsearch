package com.fileindex.controller;

import com.fileindex.dto.FileDetailDto;
import com.fileindex.exception.NotFoundException;
import com.fileindex.model.IndexedFileDocument;
import com.fileindex.repository.IndexedFileRepository;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the original file (and its extracted content/metadata) behind an indexed document.
 * Every route takes the opaque document id (a hash), never a raw filesystem path, so there is
 * no way to request an arbitrary path (e.g. "../../etc/passwd") - only files that were actually
 * indexed under a tracked root ever resolve to something.
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private static final int MAX_PREVIEW_CONTENT_LENGTH = 50_000;

    private final IndexedFileRepository repository;

    @GetMapping("/{id}")
    public FileDetailDto detail(@PathVariable String id) {
        IndexedFileDocument doc = findDocumentOrThrow(id);
        String content = doc.getContent() == null ? "" : doc.getContent();
        boolean truncated = content.length() > MAX_PREVIEW_CONTENT_LENGTH;
        String preview = truncated ? content.substring(0, MAX_PREVIEW_CONTENT_LENGTH) : content;

        return new FileDetailDto(
            doc.getId(),
            doc.getPath(),
            doc.getFileName(),
            doc.getExtension(),
            doc.getContentType(),
            doc.getSizeBytes(),
            doc.getModifiedAt(),
            doc.getAuthor(),
            doc.getDocumentTitle(),
            doc.getDocumentCreatedAt(),
            preview,
            truncated,
            doc.getTags(),
            doc.getAiTags()
        );
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable String id) throws IOException {
        return stream(id, "attachment");
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable String id) throws IOException {
        return stream(id, "inline");
    }

    private ResponseEntity<Resource> stream(String id, String disposition) throws IOException {
        IndexedFileDocument doc = findDocumentOrThrow(id);
        Path path = resolveExistingFile(doc);

        Resource resource = new FileSystemResource(path);
        MediaType mediaType = parseMediaType(doc.getContentType());
        String encodedName = URLEncoder.encode(doc.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                disposition + "; filename=\"" + doc.getFileName().replace("\"", "") + "\"; filename*=UTF-8''" + encodedName
            )
            .contentLength(Files.size(path))
            .body(resource);
    }

    private IndexedFileDocument findDocumentOrThrow(String id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Файл не найден: " + id));
    }

    private Path resolveExistingFile(IndexedFileDocument doc) {
        Path path = Path.of(doc.getPath());
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("Файл больше не существует на диске: " + doc.getPath());
        }
        return path;
    }

    private MediaType parseMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
