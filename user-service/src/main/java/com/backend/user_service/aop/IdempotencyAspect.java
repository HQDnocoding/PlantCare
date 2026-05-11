package com.backend.user_service.aop;

import com.backend.user_service.entity.IdempotencyRecord;
import com.backend.user_service.service.HttpIdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;

/**
 * AOP Aspect for handling HTTP request idempotency.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyAspect {

    private final HttpIdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            log.warn("No request context found - skipping idempotency check");
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();

        try {
            String idempotencyKey = extractIdempotencyKey(request);
            UUID userId = extractUserIdFromContext();

            if (userId == null) {
                log.warn("No user ID found - skipping idempotency check");
                return joinPoint.proceed();
            }

            // Check if this request was already processed (scoped by method + path)
            String method = request.getMethod();
            String path = request.getRequestURI();
            Optional<IdempotencyRecord> existingRecord = idempotencyService.getExistingRecord(idempotencyKey, userId,
                    method, path);

            if (existingRecord.isPresent()) {
                IdempotencyRecord record = existingRecord.get();

                String currentRequestBody = extractRequestBody(joinPoint);
                if (!idempotencyService.validateRequestConsistency(record, currentRequestBody)) {
                    log.warn("Request body mismatch for idempotency key: {}", idempotencyKey);
                    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                            .body("Request body differs from original request");
                }

                log.info("Returning cached response for duplicate request: {}", idempotencyKey);
                return reconstructResponse(record);
            }

            Object result = joinPoint.proceed();

            if (result instanceof ResponseEntity<?> responseEntity) {
                try {
                    String responseBody = objectMapper.writeValueAsString(responseEntity.getBody());
                    String requestBody = extractRequestBody(joinPoint);

                    idempotencyService.recordProcessedRequest(
                            idempotencyKey,
                            userId,
                            request.getMethod(),
                            request.getRequestURI(),
                            responseEntity.getStatusCodeValue(),
                            responseBody,
                            idempotent.ttlSeconds(),
                            requestBody);
                } catch (Exception e) {
                    log.error("Failed to cache response for idempotency", e);
                }
            }

            return result;

        } catch (Exception e) {
            log.error("Error in idempotency aspect", e);
            return joinPoint.proceed();
        }
    }

    private String extractIdempotencyKey(HttpServletRequest request) {
        String idempotencyKey = request.getHeader("X-Idempotency-Key");

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = request.getHeader("X-Correlation-Id");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            UUID userId = extractUserIdFromContext();
            idempotencyKey = idempotencyService.generateIdempotencyKey(
                    userId,
                    request.getMethod(),
                    request.getRequestURI());
        }

        return idempotencyKey;
    }

    private UUID extractUserIdFromContext() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                String principal = auth.getName();
                if (principal != null) {
                    return UUID.fromString(principal);
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract user from security context", e);
        }
        return null;
    }

    private String extractRequestBody(ProceedingJoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            if (args.length > 0) {
                Object requestDto = args[0];
                if (requestDto != null && !isHttpFrameworkClass(requestDto)) {
                    return objectMapper.writeValueAsString(requestDto);
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract request body", e);
        }
        return null;
    }

    private boolean isHttpFrameworkClass(Object obj) {
        String className = obj.getClass().getName();
        return className.startsWith("org.springframework.")
                || className.startsWith("jakarta.servlet.")
                || className.startsWith("org.springframework.http.HttpStatus");
    }

    private ResponseEntity<?> reconstructResponse(IdempotencyRecord record) {
        try {
            Object body = record.getResponseBody();
            return ResponseEntity
                    .status(record.getResponseStatus())
                    .body(body);
        } catch (Exception e) {
            log.error("Error reconstructing cached response", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
