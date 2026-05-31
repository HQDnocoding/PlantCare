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

@Slf4j
@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
public class InternalAuthController {

    private final InternalUserService internalUserService;

    @GetMapping("/users/{userId}")
    public ResponseEntity<InternalUserResponse> getUserById(@PathVariable UUID userId) {
        log.debug("Internal request received for user lookup: {}", userId);

        InternalUserResponse user = internalUserService.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        return ResponseEntity.ok(user);
    }
}