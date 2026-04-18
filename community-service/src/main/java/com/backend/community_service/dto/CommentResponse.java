package com.backend.community_service.dto;

import com.backend.community_service.entity.Comment;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class CommentResponse {
    private UUID id;
    private UUID postId;
    private UUID authorId;
    private String authorName;
    private String authorAvatar;
    private UUID parentId; // NULL cho top-level comment
    private String content;
    private int upvoteCount;
    private int downvoteCount;
    private int replyCount;
    private int score; // upvoteCount - downvoteCount
    private Short myVote; // +1, -1, hoặc null
    private Boolean isDeleted;
    private Boolean isEdited;
    private Instant deletedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static CommentResponse from(Comment comment, AuthorInfo author, Short myVote) {
        return CommentResponse.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .authorId(comment.getAuthorId())
                .authorName(author.getDisplayName())
                .authorAvatar(author.getAvatarUrl())
                .parentId(comment.getParentId())
                .content(comment.isDeleted() ? null : comment.getContent())
                .upvoteCount(comment.getUpvoteCount())
                .downvoteCount(comment.getDownvoteCount())
                .replyCount(comment.getReplyCount())
                .score(comment.getScore())
                .myVote(myVote)
                .isDeleted(comment.isDeleted())
                .isEdited(comment.isEdited())
                .deletedAt(comment.getDeletedAt())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
