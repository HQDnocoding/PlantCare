package com.backend.search_service.dto;

import lombok.Data;

@Data
public class SearchRequest {
    private String q;
    private String type = "all"; // "all" | "posts" | "users"
    private int size = 10;
}