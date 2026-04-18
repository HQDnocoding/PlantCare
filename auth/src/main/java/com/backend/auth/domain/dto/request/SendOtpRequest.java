package com.backend.auth.domain.dto.request;

import com.backend.auth.domain.entity.OtpCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SendOtpRequest {

    // Accept formats: +84901234567, 0901234567, 84901234567
    // The service layer will normalize to E.164 format (+84...)
    @NotBlank(message = "Phone number is required")
    @Pattern(
            regexp = "^\\+?[0-9]{9,15}$",
            message = "Invalid phone number format"
    )
    private String phone;

    // Use enum directly so Spring auto-validates the value.
    // If client sends "INVALID_PURPOSE", Spring returns 400 before reaching Service.
    @NotNull(message = "Purpose is required")
    private OtpCode.Purpose purpose;
}

