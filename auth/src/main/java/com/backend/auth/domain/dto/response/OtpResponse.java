package com.backend.auth.domain.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OtpResponse {

    // Whether the OTP was sent / verified successfully
    private boolean success;

    private String message;

    // Seconds until this OTP expires — client shows a countdown timer
    private long expiresInSeconds;

    private String verificationToken;
}
