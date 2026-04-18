package com.backend.scan_service.aop;

import com.backend.scan_service.entity.IdempotencyRecord;
import com.backend.scan_service.service.HttpIdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;

/**
 * AOP Aspect for HTTP request idempotency.
 *
 * Intercepts methods marked with @Idempotent annotation.
 * Implements optimistic locking pattern:
 * - Check cache before execution
 * - If hit: return 422 on request conflict, or return cached response
 * - If miss:execute method, cache response
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyAspect {

    private final HttpIdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(idempotent)")
    public Object handleIdempotentRequest(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        try {
            HttpServletRequest request = getHttpRequest();
            if (request == null) {
                return joinPoint.proceed();
            }

            // Extract headers
            String userId = request.getHeader("X-User-Id");
            String idempotencyKey = request.getHeader("X-Idempotency-Key");

            if (userId == null || idempotencyKey == null) {
                log.debug("Missing X-User-Id or X-Idempotency-Key, skipping idempotency");
                return joinPoint.proceed();
            }

            UUID userIdUUID = UUID.fromString(userId);
            String requestBody = getRequestBody(request);
            String method = request.getMethod();
            String path = request.getRequestURI();

            // 1. Check if already processed (scoped by method + path)
            Optional<IdempotencyRecord> existing = idempotencyService.getExistingRecord(idempotencyKey, userIdUUID,
                    method, path);

            if (existing.isPresent()) {
                IdempotencyRecord record = existing.get();
                log.info("Idempotency hit: key={}, user={}", idempotencyKey, userId);

                // Validate request consistency
                if (!idempotencyService.validateRequestConsistency(record, requestBody)) {
                    log.warn("Request conflict: same key but different body");
                    // Return 422 Unprocessable Entity
                    return ResponseEntity.unprocessableEntity().body(record.getResponseBody());
                }

                // Return cached response
                try {
                    Object cachedResponse = objectMapper.readValue(record.getResponseBody(), Object.class);
                    return ResponseEntity.status(record.getResponseStatus()).body(cachedResponse);
                } catch (Exception e) {
                    log.error("Failed to deserialize cached response", e);
                    // Fall through and execute anyway
                }
            }

            // 2. Not in cache, proceed with execution
            Object response = joinPoint.proceed();

            // 3. Cache the response
            try {
                int statusCode = 200;
                String responseBody = "";

                if (response instanceof ResponseEntity<?>) {
                    ResponseEntity<?> re = (ResponseEntity<?>) response;
                    statusCode = re.getStatusCode().value();
                    responseBody = objectMapper.writeValueAsString(re.getBody());
                } else {
                    responseBody = objectMapper.writeValueAsString(response);
                }

                idempotencyService.recordProcessedRequest(
                        idempotencyKey, userIdUUID, method, path,
                        statusCode, responseBody, idempotent.ttlSeconds(), requestBody);

            } catch (Exception e) {
                log.error("Failed to cache idempotent response", e);
                // Don't fail the main request if caching fails
            }

            return response;

        } catch (Throwable t) {
            log.error("Idempotency aspect error", t);
            // On error, just proceed normally
            return joinPoint.proceed();
        }
    }

    private HttpServletRequest getHttpRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private String getRequestBody(HttpServletRequest request) {
        try {
            return new String(request.getInputStream().readAllBytes());
        } catch (Exception e) {
            return null;
        }
    }
}
