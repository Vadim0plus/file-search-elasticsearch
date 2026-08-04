package com.fileindex.config;

import com.fileindex.model.IndexedFileDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * Creates the "files" index with the mapping derived from {@link IndexedFileDocument}'s
 * @Field annotations on startup. Without this, Elasticsearch would auto-create the index
 * from the first document with dynamic mapping, which infers "directory"/"extension"/"rootId"
 * as text+keyword multi-fields instead of plain keyword - breaking the prefix/terms filters
 * that assume those are top-level keyword fields.
 */
@Component
@RequiredArgsConstructor
public class ElasticsearchIndexInitializer implements ApplicationRunner {

    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public void run(ApplicationArguments args) {
        IndexOperations indexOps = elasticsearchOperations.indexOps(IndexedFileDocument.class);
        if (!indexOps.exists()) {
            indexOps.create();
            indexOps.putMapping(indexOps.createMapping());
        }
    }
}
