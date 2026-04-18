package com.backend.notification_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReplyEvent {
    private String commentId;
    private String postId;
    private String parentCommentAuthorId; // nhận notification
    private String actorId;
    private String actorName;
    private String contentPreview;
}
