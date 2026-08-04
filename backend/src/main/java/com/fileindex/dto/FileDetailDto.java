package com.fileindex.dto;

import java.time.Instant;

public record FileDetailDto(
    String id,
    String path,
    String fileName,
    String extension,
    String contentType,
    long sizeBytes,
    Instant modifiedAt,
    String author,
    String title,
    Instant documentCreatedAt,
    String content,
    boolean truncated
) {
}
