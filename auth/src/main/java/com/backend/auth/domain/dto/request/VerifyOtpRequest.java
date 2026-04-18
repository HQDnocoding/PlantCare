package com.backend.auth.domain.dto.request;


import com.backend.auth.domain.entity.OtpCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyOtpRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Invalid phone number format")
    private String phone;

    // OTP is always exactly 6 digits
    @NotBlank(message = "OTP code is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP must be 6 digits")
    private String code;

    @NotNull(message = "Purpose is required")
    private OtpCode.Purpose purpose;
}
