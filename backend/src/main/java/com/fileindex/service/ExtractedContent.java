package com.fileindex.service;

import java.time.Instant;

public record ExtractedContent(
    String text,
    String contentType,
    String author,
    String title,
    Instant createdDate
) {
}
