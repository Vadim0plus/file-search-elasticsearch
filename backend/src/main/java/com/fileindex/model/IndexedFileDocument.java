package com.fileindex.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

// indexName is versioned (not just "files") because Elasticsearch can't change an existing
// field's analyzer in place - bumping it lets a mapping change (e.g. the "russian"/"filename"
// analyzers below) take effect on a fresh index instead of requiring a migration.
//
// createIndex = false: Spring Data Elasticsearch's repository bean otherwise auto-creates the
// index as soon as it initializes, using ONLY the annotation-derived mapping with no custom
// analysis settings - which fails immediately since the fileName mapping references the
// "filename_analyzer" defined in ElasticsearchIndexInitializer. Index creation (with settings)
// is left entirely to that initializer instead.
@Document(indexName = IndexedFileDocument.INDEX_NAME, createIndex = false)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexedFileDocument {

    // SearchService queries this index directly by name via the low-level ElasticsearchClient
    // (bypassing Spring Data's repository abstraction), so it reads this same constant instead
    // of duplicating the literal - keeps the two from drifting apart on a future mapping bump.
    public static final String INDEX_NAME = "files_v3";

    // Custom analyzer name used for fileName - see ElasticsearchIndexInitializer for the actual
    // analyzer definition (a pattern tokenizer, not "russian"/"standard": both of those keep a
    // dotted name like "agreement.docx" as a single token instead of splitting it, per the
    // Unicode word-break rules the standard tokenizer follows, so a search for "agreement" alone
    // would never match).
    public static final String FILENAME_ANALYZER = "filename_analyzer";

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String path;

    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = FILENAME_ANALYZER),
        otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword)
    )
    private String fileName;

    @Field(type = FieldType.Keyword)
    private String extension;

    @Field(type = FieldType.Keyword)
    private String directory;

    @Field(type = FieldType.Text, analyzer = "russian")
    private String content;

    @Field(type = FieldType.Keyword)
    private String contentType;

    @Field(type = FieldType.Long)
    private long sizeBytes;

    @Field(type = FieldType.Date)
    private Instant modifiedAt;

    @Field(type = FieldType.Date)
    private Instant indexedAt;

    @Field(type = FieldType.Keyword)
    private String rootId;

    @Field(type = FieldType.Keyword)
    private String author;

    @Field(type = FieldType.Text)
    private String documentTitle;

    @Field(type = FieldType.Date)
    private Instant documentCreatedAt;
}
