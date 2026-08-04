package com.fileindex.controller;

import com.fileindex.dto.SearchQuery;
import com.fileindex.dto.SearchResponseDto;
import com.fileindex.service.SearchService;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private static final int MAX_PAGE_SIZE = 100;

    private final SearchService searchService;

    @GetMapping
    public SearchResponseDto search(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) List<String> extension,
        @RequestParam(required = false) List<String> tag,
        @RequestParam(required = false) String path,
        @RequestParam(required = false) Instant from,
        @RequestParam(required = false) Instant to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) throws IOException {
        int boundedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int boundedPage = Math.max(0, page);
        SearchQuery query = new SearchQuery(q, extension, tag, path, from, to, boundedPage, boundedSize);
        return searchService.search(query);
    }
}
