package com.backend.user_service.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Builder
public class UserProfileResponse {
    private UUID userId;
    private String displayName;
    private String bio;
    private String avatarUrl;
    private long followerCount;
    private long followingCount;
    @JsonProperty("isFollowedByMe")
    private boolean isFollowedByMe;
    private Instant createdAt;
}
