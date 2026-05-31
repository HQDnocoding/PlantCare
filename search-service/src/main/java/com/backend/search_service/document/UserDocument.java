package com.backend.search_service.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "users")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDocument {

    @Id
    private String id; // = userId

    @Field(type = FieldType.Text, analyzer = "standard")
    private String displayName;

    @Field(type = FieldType.Text)
    private String bio;

    @Field(type = FieldType.Keyword)
    private String avatarUrl;
}
