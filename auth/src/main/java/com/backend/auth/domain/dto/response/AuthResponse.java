package com.backend.auth.domain.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {

    private String accessToken;

    private String refreshToken;

    private long accessTokenExpiresIn;

    private UserInfo user;

    @Data
    @Builder
    public static class UserInfo {
        private String id;
        private String fullName;
        private String phone;
        private String email;
        private String avatarUrl;
        private String role;
    }
}