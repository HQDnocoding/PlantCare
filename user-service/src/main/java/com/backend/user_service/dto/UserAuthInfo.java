package com.backend.user_service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Thông tin tối thiểu auth-service trả về cho internal call.
 * Chỉ map những field cần thiết — không cần full User entity.
 */
@Getter
@Setter
@NoArgsConstructor
public class UserAuthInfo {
    private UUID id;
    private String phone;
    private String email;
    private String role;
    private boolean active;
}
