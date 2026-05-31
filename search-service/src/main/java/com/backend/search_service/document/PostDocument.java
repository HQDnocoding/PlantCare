package com.backend.search_service.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.Set;

@Document(indexName = "posts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String content;

    @Field(type = FieldType.Keyword)
    private Set<String> tags;

    @Field(type = FieldType.Keyword)
    private String authorId;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String authorName; // để search theo tên tác giả

    @Field(type = FieldType.Date)
    private Instant createdAt;

    @Field(type = FieldType.Integer)
    private int score;
}
