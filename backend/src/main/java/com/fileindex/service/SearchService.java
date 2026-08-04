package com.fileindex.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.util.NamedValue;
import com.fileindex.dto.HighlightFragmentDto;
import com.fileindex.dto.SearchHitDto;
import com.fileindex.dto.SearchQuery;
import com.fileindex.dto.SearchResponseDto;
import com.fileindex.model.IndexedFileDocument;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Builds and executes the full-text search query against Elasticsearch directly via the
 * low-level client (rather than Spring Data's repository abstraction) so we have full control
 * over multi_match fuzziness, filters and highlighting.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    private static final String INDEX = IndexedFileDocument.INDEX_NAME;

    // Control-character sentinels instead of the default <em>/</em>: real file content never
    // contains them, so splitting on them is unambiguous - and even if it somehow did, the
    // output is always inert text fragments, never markup, so there's no XSS surface either way.
    private static final String PRE_TAG = "\u0001";
    private static final String POST_TAG = "\u0002";

    private final ElasticsearchClient client;

    public SearchResponseDto search(SearchQuery searchQuery) throws IOException {
        Query textQuery = buildTextQuery(searchQuery.q());
        List<Query> filters = buildFilters(searchQuery);

        Query finalQuery = Query.of(q -> q.bool(b -> {
            b.must(textQuery);
            filters.forEach(b::filter);
            return b;
        }));

        SearchResponse<IndexedFileDocument> response = client.search(s -> s
            .index(INDEX)
            .query(finalQuery)
            .from(searchQuery.page() * searchQuery.size())
            .size(searchQuery.size())
            .highlight(h -> h
                .preTags(PRE_TAG)
                .postTags(POST_TAG)
                .fields(NamedValue.of("content", HighlightField.of(f -> f.numberOfFragments(3).fragmentSize(150))))
            ),
            IndexedFileDocument.class
        );

        List<SearchHitDto> hits = response.hits().hits().stream().map(this::toDto).toList();
        long total = response.hits().total() != null ? response.hits().total().value() : hits.size();
        return new SearchResponseDto(total, searchQuery.page(), searchQuery.size(), hits);
    }

    private Query buildTextQuery(String q) {
        if (q == null || q.isBlank()) {
            return Query.of(query -> query.matchAll(m -> m));
        }
        return Query.of(query -> query.multiMatch(m -> m
            .query(q)
            .fields("fileName^3", "content")
            .fuzziness("AUTO")
        ));
    }

    private List<Query> buildFilters(SearchQuery searchQuery) {
        List<Query> filters = new ArrayList<>();

        if (searchQuery.extensions() != null && !searchQuery.extensions().isEmpty()) {
            List<FieldValue> values = searchQuery.extensions().stream().map(FieldValue::of).toList();
            filters.add(Query.of(q -> q.terms(t -> t.field("extension").terms(tv -> tv.value(values)))));
        }

        if (searchQuery.pathPrefix() != null && !searchQuery.pathPrefix().isBlank()) {
            filters.add(Query.of(q -> q.prefix(p -> p.field("directory").value(searchQuery.pathPrefix()))));
        }

        if (searchQuery.modifiedFrom() != null || searchQuery.modifiedTo() != null) {
            filters.add(Query.of(q -> q.range(r -> r.date(d -> {
                if (searchQuery.modifiedFrom() != null) {
                    d.gte(DateTimeFormatter.ISO_INSTANT.format(searchQuery.modifiedFrom()));
                }
                if (searchQuery.modifiedTo() != null) {
                    d.lte(DateTimeFormatter.ISO_INSTANT.format(searchQuery.modifiedTo()));
                }
                return d.field("modifiedAt");
            }))));
        }

        return filters;
    }

    private SearchHitDto toDto(Hit<IndexedFileDocument> hit) {
        IndexedFileDocument doc = hit.source();
        List<List<HighlightFragmentDto>> highlights = List.of();
        if (hit.highlight() != null && hit.highlight().containsKey("content")) {
            highlights = hit.highlight().get("content").stream().map(this::splitFragment).toList();
        }
        return new SearchHitDto(
            doc.getId(),
            doc.getPath(),
            doc.getFileName(),
            doc.getExtension(),
            doc.getSizeBytes(),
            doc.getModifiedAt(),
            highlights,
            "/api/files/" + doc.getId() + "/download"
        );
    }

    List<HighlightFragmentDto> splitFragment(String fragment) {
        List<HighlightFragmentDto> parts = new ArrayList<>();
        int i = 0;
        while (i < fragment.length()) {
            int start = fragment.indexOf(PRE_TAG, i);
            if (start == -1) {
                parts.add(new HighlightFragmentDto(fragment.substring(i), false));
                break;
            }
            if (start > i) {
                parts.add(new HighlightFragmentDto(fragment.substring(i, start), false));
            }
            int end = fragment.indexOf(POST_TAG, start);
            if (end == -1) {
                parts.add(new HighlightFragmentDto(fragment.substring(start + PRE_TAG.length()), true));
                break;
            }
            parts.add(new HighlightFragmentDto(fragment.substring(start + PRE_TAG.length(), end), true));
            i = end + POST_TAG.length();
        }
        return parts;
    }
}
