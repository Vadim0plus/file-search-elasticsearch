package com.fileindex.dto;

/** A slice of a highlighted snippet: plain text plus whether it matched the search query. */
public record HighlightFragmentDto(String text, boolean matched) {
}
