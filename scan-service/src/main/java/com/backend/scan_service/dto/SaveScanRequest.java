package com.backend.scan_service.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class SaveScanRequest {
    @NotNull
    private MultipartFile image;
    @NotBlank  private String disease;
    @NotNull @DecimalMin("0.0") @DecimalMax("1.0")
    private Double confidence;
    @NotNull   private Boolean confidentEnough;
}