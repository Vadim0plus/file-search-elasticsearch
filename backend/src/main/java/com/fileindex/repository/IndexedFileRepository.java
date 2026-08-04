package com.fileindex.repository;

import com.fileindex.model.IndexedFileDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface IndexedFileRepository extends ElasticsearchRepository<IndexedFileDocument, String> {
}
