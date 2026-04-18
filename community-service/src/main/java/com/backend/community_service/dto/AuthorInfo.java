package com.backend.community_service.dto;

import lombok.*;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthorInfo {
    private UUID userId;
    private String displayName;
    private String avatarUrl;

    /** Fallback khi user-service không phản hồi */
    public static AuthorInfo unknown(UUID userId) {
        return AuthorInfo.builder()
                .userId(userId)
                .displayName("Người dùng")
                .avatarUrl(null)
                .build();
    }
}
