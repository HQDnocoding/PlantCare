package com.backend.community_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentRepliedEvent {
    private String commentId;
    private String postId;
    private String parentCommentAuthorId;
    private String actorId;
    private String actorName;
    private String contentPreview;
}