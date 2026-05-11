package com.backend.user_service.controller;

import com.backend.user_service.dto.UserStatsResponse;
import com.backend.user_service.service.UserStatsService;
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
public class UserStatsController {

    private final UserStatsService userStatsService;

    @GetMapping("/users")
    public ResponseEntity<UserStatsResponse> getUserStats() {
        log.info("[Stats] GET /internal/v1/stats/users");
        return ResponseEntity.ok(userStatsService.getUserStats());
    }
}
