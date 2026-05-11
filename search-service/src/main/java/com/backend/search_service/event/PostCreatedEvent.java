package com.backend.search_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

// event/PostCreatedEvent.java  — dùng chung cho community-service và search-service
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostCreatedEvent {
    private String postId;
    private String authorId;
    private String authorName;   // denormalize luôn để search-service không cần gọi user-service
    private String content;
    private Set<String> tags;
    private Instant createdAt;
    private int score;
}
