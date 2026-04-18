package com.backend.community_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityStatsResponse {

    @JsonProperty("total_posts")
    private long totalPosts;

    @JsonProperty("total_comments")
    private long totalComments;

    @JsonProperty("total_votes")
    private long totalVotes;

    @JsonProperty("new_posts_this_week")
    private long newPostsThisWeek;

    @JsonProperty("new_comments_this_week")
    private long newCommentsThisWeek;

    @JsonProperty("active_communities")
    private long activeCommunities;

    @JsonProperty("timestamp")
    private long timestamp;
}
