package com.fileindex.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
    // Bumped to v4 when the tags/aiTags fields were added (see the class comment above for why
    // a mapping change means a new index name rather than an in-place update).
    public static final String INDEX_NAME = "files_v4";

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

    // All tags currently active on the file - both AI-suggested and manually added - used for
    // the tag search filter. aiTags is a subset marking which of these came from AI generation,
    // kept around after generation so the UI can badge them even once merged into this list.
    @Field(type = FieldType.Keyword)
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Field(type = FieldType.Keyword)
    @Builder.Default
    private List<String> aiTags = new ArrayList<>();
}
