package com.fileindex.dto;

import java.util.List;

public record SearchResponseDto(long total, int page, int size, List<SearchHitDto> results) {
}
