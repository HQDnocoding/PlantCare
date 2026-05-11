package com.backend.community_service.dto;

import com.backend.community_service.entity.Post;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Builder
public class PostResponse {
    private UUID id;
    private UUID authorId;
    private String authorName;
    private String authorAvatar;
    private String content;
    private List<String> imageUrls;
    private Set<String> tags;
    private int upvoteCount;
    private int downvoteCount;
    private int score;
    private int commentCount;
    private Short myVote; // +1, -1, hoặc null nếu chưa vote / guest
    private Instant createdAt;
    private Instant updatedAt;

    public static PostResponse from(Post post, com.backend.community_service.dto.AuthorInfo author, Short myVote) {
        return PostResponse.builder()
                .id(post.getId())
                .authorId(post.getAuthorId())
                .authorName(author.getDisplayName())
                .authorAvatar(author.getAvatarUrl())
                .content(post.getContent())
                .imageUrls(post.getImageUrls() != null ? List.of(post.getImageUrls()) : List.of())
                .tags(post.getTags())
                .upvoteCount(post.getUpvoteCount())
                .downvoteCount(post.getDownvoteCount())
                .score(post.getScore())
                .commentCount(post.getCommentCount())
                .myVote(myVote)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}
