package com.fileindex.config;

import com.fileindex.model.IndexedFileDocument;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * Creates the index with the mapping derived from {@link IndexedFileDocument}'s @Field
 * annotations on startup, plus a custom analysis configuration for the fileName field (see
 * FILENAME_SETTINGS). Without explicit index creation, Elasticsearch would auto-create the
 * index from the first document with dynamic mapping, which infers "directory"/"extension"/
 * "rootId" as text+keyword multi-fields instead of plain keyword - breaking the prefix/terms
 * filters that assume those are top-level keyword fields.
 */
@Component
@RequiredArgsConstructor
public class ElasticsearchIndexInitializer implements ApplicationRunner {

    // The standard/"russian" analyzers' tokenizer follows Unicode word-break rules that keep a
    // single dot between two letter sequences from splitting a word (the same rule that keeps
    // "example.com" as one token) - so "agreement.docx" indexes as one token and a search for
    // "agreement" alone never matches. A pattern tokenizer splitting on any non-letter/non-digit
    // run fixes that, while the Russian stemmer still lets a declined word in a filename match
    // its base form, same as the content field.
    private static final Map<String, Object> FILENAME_SETTINGS = Map.of(
        "index.analysis.tokenizer.filename_tokenizer.type", "pattern",
        "index.analysis.tokenizer.filename_tokenizer.pattern", "[^\\p{L}\\p{N}]+",
        "index.analysis.filter.filename_stemmer.type", "stemmer",
        "index.analysis.filter.filename_stemmer.language", "russian",
        "index.analysis.analyzer." + IndexedFileDocument.FILENAME_ANALYZER + ".type", "custom",
        "index.analysis.analyzer." + IndexedFileDocument.FILENAME_ANALYZER + ".tokenizer", "filename_tokenizer",
        "index.analysis.analyzer." + IndexedFileDocument.FILENAME_ANALYZER + ".filter", List.of("lowercase", "filename_stemmer")
    );

    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public void run(ApplicationArguments args) {
        IndexOperations indexOps = elasticsearchOperations.indexOps(IndexedFileDocument.class);
        if (!indexOps.exists()) {
            indexOps.create(FILENAME_SETTINGS, indexOps.createMapping());
        }
    }
}
