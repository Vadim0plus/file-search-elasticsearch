package com.fileindex.controller;

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
 * Streams the original file behind an indexed document. The route takes the opaque document
 * id (a hash), never a raw filesystem path, so there is no way to request an arbitrary path
 * (e.g. "../../etc/passwd") - only files that were actually indexed under a tracked root
 * ever resolve to something.
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileDownloadController {

    private final IndexedFileRepository repository;

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable String id) throws IOException {
        IndexedFileDocument doc = repository.findById(id)
            .orElseThrow(() -> new NotFoundException("Файл не найден: " + id));

        Path path = Path.of(doc.getPath());
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("Файл больше не существует на диске: " + doc.getPath());
        }

        Resource resource = new FileSystemResource(path);
        MediaType mediaType = parseMediaType(doc.getContentType());
        String encodedName = URLEncoder.encode(doc.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + doc.getFileName().replace("\"", "") + "\"; filename*=UTF-8''" + encodedName
            )
            .contentLength(Files.size(path))
            .body(resource);
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
