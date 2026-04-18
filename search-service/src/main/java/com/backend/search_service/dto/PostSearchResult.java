package com.backend.search_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Set;

@Data
@Builder
public class PostSearchResult {
    private String postId;
    private String authorId;
    private String authorName;
    private String content;
    private Set<String> tags;
    private int score;
    private Instant createdAt;
}