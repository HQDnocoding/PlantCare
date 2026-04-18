package com.backend.auth.controller;

import com.backend.auth.domain.dto.response.InternalUserResponse;
import com.backend.auth.service.InternalUserService;
import com.backend.auth.exception.AppException;
import com.backend.auth.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller for internal system operations.
 * These endpoints are shielded by InternalSecretFilter and should
 * NEVER be exposed to the public internet.
 */
@Slf4j
@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
public class InternalAuthController {

    private final InternalUserService internalUserService;

    /**
     * Fetch user identity and status for other microservices.
     * Used for authorization checks or profile enrichment in downstream services.
     * * @param userId Unique identifier of the user
     * 
     * @return InternalUserResponse containing basic identity and status
     * @throws AppException if user is not found or deleted
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<InternalUserResponse> getUserById(@PathVariable UUID userId) {
        log.debug("Internal request received for user lookup: {}", userId);

        InternalUserResponse user = internalUserService.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        return ResponseEntity.ok(user);
    }
}