package com.backend.auth.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {

    // The raw refresh token string sent by the client.
    // Service will hash it and look up in DB.
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}

