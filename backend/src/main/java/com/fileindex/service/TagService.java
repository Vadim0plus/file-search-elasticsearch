package com.fileindex.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fileindex.dto.TagCountDto;
import com.fileindex.dto.TagsDto;
import com.fileindex.exception.NotFoundException;
import com.fileindex.model.IndexedFileDocument;
import com.fileindex.repository.IndexedFileRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Owns tag mutation (AI-generated and manual) and the tag-cloud aggregation used for search
 * filtering / autocomplete. Manual and AI-suggested tags both live in the same {@code tags} list
 * so both are searchable the same way; {@code aiTags} is kept alongside only to let the UI badge
 * which of a file's tags came from AI generation.
 */
@Service
@RequiredArgsConstructor
public class TagService {

    private static final String INDEX = IndexedFileDocument.INDEX_NAME;
    private static final int MAX_DISTINCT_TAGS = 500;

    private final IndexedFileRepository repository;
    private final AiTaggingService aiTaggingService;
    private final ElasticsearchClient client;

    public TagsDto generateTags(String id) {
        IndexedFileDocument doc = findOrThrow(id);
        List<String> generated = aiTaggingService.generateTags(doc.getFileName(), doc.getContent());

        Set<String> tags = new LinkedHashSet<>(doc.getTags());
        Set<String> aiTags = new LinkedHashSet<>(doc.getAiTags());
        tags.addAll(generated);
        aiTags.addAll(generated);

        doc.setTags(new ArrayList<>(tags));
        doc.setAiTags(new ArrayList<>(aiTags));
        repository.save(doc);
        return toDto(doc);
    }

    public TagsDto addTag(String id, String tag) {
        String normalized = normalize(tag);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Метка не может быть пустой");
        }

        IndexedFileDocument doc = findOrThrow(id);
        Set<String> tags = new LinkedHashSet<>(doc.getTags());
        tags.add(normalized);
        doc.setTags(new ArrayList<>(tags));
        repository.save(doc);
        return toDto(doc);
    }

    public TagsDto removeTag(String id, String tag) {
        String normalized = normalize(tag);
        IndexedFileDocument doc = findOrThrow(id);

        List<String> tags = new ArrayList<>(doc.getTags());
        tags.remove(normalized);
        List<String> aiTags = new ArrayList<>(doc.getAiTags());
        aiTags.remove(normalized);

        doc.setTags(tags);
        doc.setAiTags(aiTags);
        repository.save(doc);
        return toDto(doc);
    }

    /** Distinct tags across the whole index with document counts, for filter chips/autocomplete. */
    public List<TagCountDto> listTags() throws IOException {
        SearchResponse<Void> response = client.search(s -> s
            .index(INDEX)
            .size(0)
            .aggregations("tags", a -> a.terms(t -> t.field("tags").size(MAX_DISTINCT_TAGS))),
            Void.class);

        return response.aggregations().get("tags").sterms().buckets().array().stream()
            .map(bucket -> new TagCountDto(bucket.key().stringValue(), bucket.docCount()))
            .toList();
    }

    static String normalize(String tag) {
        return tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private IndexedFileDocument findOrThrow(String id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Файл не найден: " + id));
    }

    private TagsDto toDto(IndexedFileDocument doc) {
        return new TagsDto(doc.getTags(), doc.getAiTags());
    }
}
