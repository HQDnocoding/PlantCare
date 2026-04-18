package com.backend.auth.aop;

import com.backend.auth.entity.IdempotencyRecord;
import com.backend.auth.service.HttpIdempotencyService;
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
 * AOP Aspect for handling HTTP request idempotency (Auth Service).
 *
 * Intercepts methods annotated with @Idempotent and:
 * 1. Extracts idempotency key from request headers
 * 2. Checks if this request was already processed
 * 3. If yes and request matches: returns cached response
 * 4. If yes and request differs: returns 422 Unprocessable Entity
 * 5. If no: executes method and caches the response
 *
 * Header Priority:
 * - X-Idempotency-Key: Client-provided unique key (standard RFC 9110)
 * - X-Correlation-Id: Fallback (set by API Gateway)
 * - Generated: Auto-generate if neither provided
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
            // Extract or generate idempotency key
            String idempotencyKey = extractIdempotencyKey(request);
            UUID userId = extractUserIdFromContext();

            // Note: Auth service might allow unauthenticated requests
            // If no userId, still allow (use null or correlation ID as fallback)
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                log.warn("No idempotency key available - proceeding without dedup");
                return joinPoint.proceed();
            }

            // For unauthenticated requests (login, guest), use empty UUID as placeholder
            if (userId == null) {
                userId = UUID.fromString("00000000-0000-0000-0000-000000000000");
            }

            // Check if this request was already processed (scoped by method + path)
            String method = request.getMethod();
            String path = request.getRequestURI();
            Optional<IdempotencyRecord> existingRecord = idempotencyService.getExistingRecord(idempotencyKey, userId,
                    method, path);

            if (existingRecord.isPresent()) {
                IdempotencyRecord record = existingRecord.get();

                // Validate that retry request has same body as original
                String currentRequestBody = extractRequestBody(joinPoint);
                if (!idempotencyService.validateRequestConsistency(record, currentRequestBody)) {
                    log.warn("Request body mismatch for idempotency key: {}", idempotencyKey);
                    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                            .body("Request body differs from original request");
                }

                // Return cached response
                log.info("Returning cached response for duplicate request: {}", idempotencyKey);
                return reconstructResponse(record);
            }

            // Process the request for the first time
            Object result = joinPoint.proceed();

            // Cache the response
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
                    // Continue anyway - idempotency recording failure shouldn't fail the main
                    // operation
                }
            }

            return result;

        } catch (Exception e) {
            log.error("Error in idempotency aspect", e);
            // On error, just proceed with unguarded execution
            return joinPoint.proceed();
        }
    }

    /**
     * Extract idempotency key from request headers.
     * Priority:
     * 1. X-Idempotency-Key header (explicit)
     * 2. X-Correlation-Id header (fallback)
     * 3. Generate from request components
     */
    private String extractIdempotencyKey(HttpServletRequest request) {
        String idempotencyKey = request.getHeader("X-Idempotency-Key");

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            // Fallback to Correlation ID
            idempotencyKey = request.getHeader("X-Correlation-Id");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            // Generate from request components
            UUID userId = extractUserIdFromContext();
            if (userId != null) {
                idempotencyKey = idempotencyService.generateIdempotencyKey(
                        userId,
                        request.getMethod(),
                        request.getRequestURI());
            }
        }

        return idempotencyKey;
    }

    /**
     * Extract user ID from Security Context
     */
    private UUID extractUserIdFromContext() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
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

    /**
     * Extract request body from method arguments
     */
    private String extractRequestBody(ProceedingJoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            if (args.length > 0) {
                // Typically the first argument is the request DTO
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

    /**
     * Check if object is a Spring HTTP framework class (not user DTO)
     */
    private boolean isHttpFrameworkClass(Object obj) {
        String className = obj.getClass().getName();
        return className.startsWith("org.springframework.")
                || className.startsWith("jakarta.servlet.")
                || className.startsWith("org.springframework.http.HttpStatus");
    }

    /**
     * Reconstruct ResponseEntity from cached IdempotencyRecord
     */
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
