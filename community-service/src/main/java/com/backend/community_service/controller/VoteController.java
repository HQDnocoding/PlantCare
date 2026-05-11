package com.backend.community_service.controller;

import com.backend.community_service.aop.Idempotent;
import com.backend.community_service.dto.ApiResponse;
import com.backend.community_service.entity.Vote;
import com.backend.community_service.service.VoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/votes")
@RequiredArgsConstructor
@Slf4j
@Validated
public class VoteController {

    private final VoteService voteService;

    @PostMapping
    @Idempotent(ttlSeconds = 86400)
    public ResponseEntity<ApiResponse<Short>> vote(
            @RequestParam UUID targetId,
            @RequestParam Vote.TargetType targetType,
            @RequestParam short value,
            Authentication auth) {

        UUID userId = UUID.fromString(auth.getName());
        Short result = voteService.vote(userId, targetId, targetType, value);

        String message = result == null ? "Vote removed" : "Vote recorded";
        return ResponseEntity.ok(ApiResponse.ok(result, message));
    }

    @PostMapping("/post/{postId}")
    @Idempotent(ttlSeconds = 86400)
    public ResponseEntity<ApiResponse<Short>> votePost(
            @PathVariable UUID postId,
            @RequestParam short value,
            Authentication auth) {

        UUID userId = UUID.fromString(auth.getName());
        Short result = voteService.vote(userId, postId, Vote.TargetType.POST, value);

        String message = result == null ? "Vote removed" : "Vote recorded";
        return ResponseEntity.ok(ApiResponse.ok(result, message));
    }

    @PostMapping("/comment/{commentId}")
    @Idempotent(ttlSeconds = 86400)
    public ResponseEntity<ApiResponse<Short>> voteComment(
            @PathVariable UUID commentId,
            @RequestParam short value,
            Authentication auth) {

        UUID userId = UUID.fromString(auth.getName());
        Short result = voteService.vote(userId, commentId, Vote.TargetType.COMMENT, value);

        String message = result == null ? "Vote removed" : "Vote recorded";
        return ResponseEntity.ok(ApiResponse.ok(result, message));
    }
}
