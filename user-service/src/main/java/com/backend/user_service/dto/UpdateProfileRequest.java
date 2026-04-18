package com.backend.user_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// ─── Update profile (tên + bio) ─────────────────────────────────────────────
@Getter
@Setter
public class UpdateProfileRequest {

    @NotBlank(message = "Display name must not be blank")
    @Size(min = 2, max = 64, message = "Display name must be between 2 and 64 characters")
    private String displayName;

    @Size(max = 300, message = "Bio must not exceed 300 characters")
    private String bio;
}
