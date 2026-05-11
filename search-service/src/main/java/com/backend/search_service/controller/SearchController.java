package com.backend.search_service.controller;

import com.backend.search_service.dto.ApiResponse;
import com.backend.search_service.dto.SearchRequest;
import com.backend.search_service.dto.SearchResponse;
import com.backend.search_service.service.SearchCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchCacheService searchCacheService;

    @GetMapping
    public ResponseEntity<ApiResponse<SearchResponse>> search(
            @ModelAttribute SearchRequest request,
            @RequestParam(defaultValue = "0") int page) {

        if (request.getQ() == null || request.getQ().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<SearchResponse>builder()
                            .message("Query must not be empty")
                            .build());
        }

        request.setQ(request.getQ().trim());
        SearchResponse response = searchCacheService.searchWithCache(request, page);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}