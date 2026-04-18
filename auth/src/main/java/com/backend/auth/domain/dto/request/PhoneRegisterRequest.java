package com.backend.auth.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PhoneRegisterRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Invalid phone number format")
    private String phone;

    // The OTP verification token returned after verifying OTP successfully.
    // Client must complete OTP verification first, then send this token here.
    // This prevents registering without a verified phone.
    @NotBlank(message = "OTP verification token is required")
    private String otpVerificationToken;

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    private String fullName;

    // Password is optional — user can register phone-only and set password later
    // If provided, must be at least 8 chars with at least one letter and one digit
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
            message = "Password must be at least 8 characters with letters and numbers"
    )
    private String password;
}
