package com.backend.auth.domain.dto.request;

import com.backend.auth.domain.entity.SocialAccount;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SocialLoginRequest {

    // GOOGLE or FACEBOOK
    @NotNull(message = "Provider is required")
    private SocialAccount.Provider provider;

    @NotBlank(message = "Token is required")
    private String token;
    private String deviceInfo;
}