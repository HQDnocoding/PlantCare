package com.backend.auth.domain.dto.response;

import com.backend.auth.domain.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class AdminUserResponse {
    private UUID id;
    private String fullName;
    private String email;
    private String phone;
    private String role;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static AdminUserResponse from(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
