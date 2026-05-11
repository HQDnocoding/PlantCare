package com.backend.auth.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PhoneLoginRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Invalid phone number format")
    private String phone;

    @NotBlank(message = "Password is required")
    private String password;

    // Optional: sent by mobile app to show on "active sessions" screen
    // Example: "Android 14 / Samsung Galaxy S23"
    private String deviceInfo;
}

