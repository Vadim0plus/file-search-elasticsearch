package com.fileindex.dto;

import java.time.Instant;
import java.util.List;

public record SearchQuery(
    String q,
    List<String> extensions,
    String pathPrefix,
    Instant modifiedFrom,
    Instant modifiedTo,
    int page,
    int size
) {
}
