package com.backend.api_gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * First filter in the chain. Handles correlation ID propagation and access
 * logging.
 *
 * Intentionally does NOT log request bodies or auth headers
 * to avoid leaking passwords or OTP codes.
 */
@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

        private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                ServerHttpRequest request = exchange.getRequest();
                long startTime = Instant.now().toEpochMilli();

                String method = request.getMethod().name();
                String path = request.getURI().getPath();

                // Reuse client-supplied ID for end-to-end tracing, or generate a short one
                String initialId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
                final String correlationId = (initialId == null || initialId.isEmpty())
                                ? UUID.randomUUID().toString().substring(0, 8)
                                : initialId;

                // Log Idempotency-Key presence for POST requests (helps debug missing headers)
                String idempotencyKey = request.getHeaders().getFirst("X-Idempotency-Key");
                String idempotencyLog = ("POST".equals(method))
                                ? (idempotencyKey != null ? " | idempotencyKey=present"
                                                : " | WARN: idempotencyKey=MISSING")
                                : "";

                ServerHttpRequest mutatedRequest = request.mutate()
                                .header(CORRELATION_ID_HEADER, correlationId)
                                .build();

                log.info("[GW] --> {} {} | correlationId={}{}", method, path, correlationId, idempotencyLog);

                return chain.filter(exchange.mutate().request(mutatedRequest).build())
                                .doOnError(e -> log.error("[GW] EXCEPTION | {} {} | correlationId={} | error={}",
                                                method, path, correlationId, e.getMessage(), e))
                                .then(Mono.fromRunnable(() -> {
                                        // .then() runs after the full response is committed — safe to read status here
                                        ServerHttpResponse response = exchange.getResponse();
                                        long latency = Instant.now().toEpochMilli() - startTime;
                                        int status = response.getStatusCode() != null
                                                        ? response.getStatusCode().value()
                                                        : 0;

                                        // Log level follows HTTP status tier for easier log filtering
                                        if (status >= 500) {
                                                log.error("[GW] <-- {} {} | status={} | {}ms | correlationId={}",
                                                                method, path, status, latency, correlationId);
                                        } else if (status >= 400) {
                                                log.warn("[GW] <-- {} {} | status={} | {}ms | correlationId={}",
                                                                method, path, status, latency, correlationId);
                                        } else {
                                                log.info("[GW] <-- {} {} | status={} | {}ms | correlationId={}",
                                                                method, path, status, latency, correlationId);
                                        }
                                }));
        }

        @Override
        public int getOrder() {
                return Ordered.HIGHEST_PRECEDENCE + 1;
        }
}