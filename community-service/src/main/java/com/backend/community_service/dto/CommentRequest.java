package com.backend.community_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommentRequest {

    @NotBlank(message = "Nội dung bình luận không được để trống")
    @Size(min = 1, max = 2000, message = "Nội dung bình luận tối đa 2000 ký tự")
    private String content;
}