package com.fileindex.dto;

import java.time.Instant;
import java.util.List;

public record SearchHitDto(
    String id,
    String path,
    String fileName,
    String extension,
    long sizeBytes,
    Instant modifiedAt,
    List<List<HighlightFragmentDto>> highlights,
    String downloadUrl,
    List<String> tags
) {
}
