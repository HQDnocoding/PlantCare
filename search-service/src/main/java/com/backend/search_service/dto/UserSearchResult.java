package com.backend.search_service.dto;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserSearchResult {
    private String userId;
    private String displayName;
    private String bio;
    private String avatarUrl;
}