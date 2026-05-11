package com.backend.community_service.controller;

import com.backend.community_service.dto.CommunityStatsResponse;
import com.backend.community_service.service.CommunityStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/stats")
@RequiredArgsConstructor
@Slf4j
public class CommunityStatsController {

    private final CommunityStatsService communityStatsService;

    @GetMapping("/community")
    public ResponseEntity<CommunityStatsResponse> getCommunityStats() {
        log.info("[Stats] GET /internal/v1/stats/community");
        return ResponseEntity.ok(communityStatsService.getCommunityStats());
    }
}
