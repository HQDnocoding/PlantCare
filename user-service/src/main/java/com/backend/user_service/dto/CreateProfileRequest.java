package com.backend.user_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProfileRequest {
    @NotNull(message = "userId is required")
    private UUID userId;

    @NotBlank(message = "displayName is required")
    @Size(min = 2, max = 64, message = "displayName must be between 2 and 64 characters")
    private String displayName;

    private String avatarUrl;
}