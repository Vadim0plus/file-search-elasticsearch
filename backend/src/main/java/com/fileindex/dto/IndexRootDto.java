package com.fileindex.dto;

import com.fileindex.indexroot.IndexRootStatus;
import java.time.Instant;

public record IndexRootDto(
    String id,
    String path,
    IndexRootStatus status,
    int totalFiles,
    int processedFiles,
    long docCount,
    String lastError,
    Instant createdAt
) {
}
