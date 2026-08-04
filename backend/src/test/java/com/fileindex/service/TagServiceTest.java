package com.fileindex.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fileindex.dto.TagsDto;
import com.fileindex.exception.NotFoundException;
import com.fileindex.model.IndexedFileDocument;
import com.fileindex.repository.IndexedFileRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TagServiceTest {

    private IndexedFileRepository repository;
    private AiTaggingService aiTaggingService;
    private TagService tagService;

    @BeforeEach
    void setUp() {
        repository = mock(IndexedFileRepository.class);
        aiTaggingService = mock(AiTaggingService.class);
        tagService = new TagService(repository, aiTaggingService, mock(ElasticsearchClient.class));
    }

    @Test
    void generateTagsMergesAiSuggestionsIntoTagsAndAiTags() {
        IndexedFileDocument doc = IndexedFileDocument.builder()
            .id("doc-1")
            .fileName("agreement.pdf")
            .content("...")
            .tags(List.of("важное"))
            .aiTags(List.of())
            .build();
        when(repository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(aiTaggingService.generateTags("agreement.pdf", "...")).thenReturn(List.of("договор"));

        TagsDto result = tagService.generateTags("doc-1");

        assertThat(result.tags()).containsExactlyInAnyOrder("важное", "договор");
        assertThat(result.aiTags()).containsExactly("договор");
    }

    @Test
    void addTagNormalizesAndDeduplicates() {
        IndexedFileDocument doc = IndexedFileDocument.builder().id("doc-1").tags(List.of("важное")).aiTags(List.of()).build();
        when(repository.findById("doc-1")).thenReturn(Optional.of(doc));

        TagsDto result = tagService.addTag("doc-1", "  ВАЖНОЕ  ");

        assertThat(result.tags()).containsExactly("важное");
    }

    @Test
    void addTagRejectsBlankInput() {
        assertThatThrownBy(() -> tagService.addTag("doc-1", "   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeTagDropsFromBothTagsAndAiTags() {
        IndexedFileDocument doc = IndexedFileDocument.builder()
            .id("doc-1")
            .tags(List.of("договор", "важное"))
            .aiTags(List.of("договор"))
            .build();
        when(repository.findById("doc-1")).thenReturn(Optional.of(doc));

        TagsDto result = tagService.removeTag("doc-1", "договор");

        assertThat(result.tags()).containsExactly("важное");
        assertThat(result.aiTags()).isEmpty();
    }

    @Test
    void throwsNotFoundForUnknownFile() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.addTag("missing", "тег")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void generateTagsPropagatesAiFailure() {
        IndexedFileDocument doc = IndexedFileDocument.builder().id("doc-1").fileName("a.txt").content("x").build();
        when(repository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(aiTaggingService.generateTags(any(), any())).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> tagService.generateTags("doc-1")).isInstanceOf(RuntimeException.class);
    }
}
