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

@Document(indexName = "files")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexedFileDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String path;

    @MultiField(
        mainField = @Field(type = FieldType.Text),
        otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword)
    )
    private String fileName;

    @Field(type = FieldType.Keyword)
    private String extension;

    @Field(type = FieldType.Keyword)
    private String directory;

    @Field(type = FieldType.Text)
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
}
