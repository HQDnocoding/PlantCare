package com.backend.scan_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateConvRequest {
    @NotBlank private String convId;
}
