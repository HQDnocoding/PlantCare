package com.backend.notification_service.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PostCreatedEvent {
    private String postId;
    private String authorId;
    private String authorName;
    private String content;
}
