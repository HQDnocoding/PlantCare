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

    // Only returned after successful OTP verification (not after send).
    // Client must include this token in the registration request to prove
    // the phone was actually verified in this session.
    // It's a short-lived signed JWT with claim: { phone, purpose, verified: true }
    private String verificationToken;
}

