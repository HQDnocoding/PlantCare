package com.backend.community_service.dto;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PostCreateRequest {
    @NotBlank(message = "Nội dung không được để trống")
    private String content;
    private List<String> tags;
    private List<MultipartFile> images;
}