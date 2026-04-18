package com.backend.api_gateway.filter;

import com.backend.api_gateway.exception.AppException;
import com.backend.api_gateway.exception.ErrorCode;
import com.backend.api_gateway.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final JwtService jwtService;

    // Paths that bypass JWT validation (unauthenticated access allowed)
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/otp/send",
            "/api/v1/auth/otp/verify",
            "/api/v1/auth/login/phone",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/privacy-policy",
            "api/v1/auth/facebook/delete-data",
            "/api/v1/auth/login/social",
            "/api/v1/search",
            "/api/v1/auth/guest",
            "/api/v1/diseases",
            "/fallback");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        log.debug("[JwtFilter] --> path={}", path);

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Mono.error(new AppException(ErrorCode.ACCESS_TOKEN_MISSING));
        }

        String token = authHeader.substring(7);

        try {
            String role = jwtService.extractRole(token);
            log.info("[JwtFilter] Role extracted | role={} | path={}", role, path);

            // GUEST tokens are read-only — block any write operations
            String method = exchange.getRequest().getMethod().name();
            if ("GUEST".equals(role) && !"GET".equals(method)) {
                log.warn("[JwtFilter] GUEST token attempted write operation | method={} | path={}", method, path);
                return Mono.error(new AppException(ErrorCode.FORBIDDEN));
            }

            UUID userId = !"GUEST".equals(role) ? jwtService.extractUserId(token) : null;

            String idempotencyKey = exchange.getRequest()
                    .getHeaders()
                    .getFirst("X-Idempotency-Key");

            // Build the request with all header operations in a single lambda
            // to ensure atomicity and prevent header conflicts
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.remove("X-User-Id");
                        headers.remove("X-User-Role");
                        // Always set X-User-Role
                        headers.set("X-User-Role", role != null ? role : "");
                        // X-User-Id is only set for authenticated users (not GUEST)
                        if (userId != null) {
                            headers.set("X-User-Id", userId.toString());
                        }
                        // Preserve Idempotency-Key for downstream idempotency enforcement
                        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                            headers.set("X-Idempotency-Key", idempotencyKey);
                        }
                    })
                    .build();

            log.debug("JWT verified | userId={} role={} path={}", userId, role, path);

            // Forward mutated request; log any downstream errors without swallowing them
            return chain.filter(exchange.mutate().request(mutatedRequest).build())
                    .doOnError(e -> log.error("[JwtFilter] Downstream error | path={} | error={}",
                            path, e.getMessage(), e));

        } catch (Exception e) {
            // Covers expired, malformed, or bad-signature tokens
            log.error("[JwtFilter] Exception | path={} | error={}", path, e.getMessage(), e);
            return Mono.error(new AppException(ErrorCode.ACCESS_TOKEN_INVALID));
        }
    }

    // Matches by prefix so query strings (?foo=bar) don't break the check
    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}