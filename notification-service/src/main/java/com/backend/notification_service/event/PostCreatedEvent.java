package com.backend.notification_service.event;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PostCreatedEvent {
    private String postId;
    private String authorId;
    private String authorName;
    private String content;
    private List<String> tags;
    private Instant createdAt;
    private int score;
}
