package com.backend.community_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostUpdatedEvent {
    private String authorId;
    private String authorName;
    private String postId;
    private String content;
    private Set<String> tags;
    private Instant updatedAt;
}
