package com.backend.notification_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentRepliedEvent {
    private String commentId;
    private String postId;
    private String parentCommentAuthorId;
    private String actorId;
    private String actorName;
    private String contentPreview;
}
