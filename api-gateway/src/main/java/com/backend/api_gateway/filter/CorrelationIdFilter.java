package com.backend.api_gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Filter to generate and propagate Correlation IDs across the entire request
 * chain.
 *
 * Correlation ID (X-Correlation-Id):
 * - Unique identifier for tracking a single request through all services
 * - Generated here at API Gateway (entry point)
 * - Propagated downstream via header + MDC
 * - Used for:
 * 1. Distributed tracing (correlate logs across services)
 * 2. Idempotency fallback (if X-Idempotency-Key not provided)
 * 3. Debugging (trace a user action through entire system)
 *
 * Strategy:
 * - If client sends X-Correlation-Id → preserve it
 * - If client doesn't send → generate UUID
 * - Forward to all downstream services
 *
 * Execution Order:
 * This filter runs FIRST (HIGHEST_PRECEDENCE) so X-Correlation-Id is available
 * for other filters like IdempotencyKeyFilter.
 */
@Slf4j
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Step 1: Extract or generate Correlation ID
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);

        if (correlationId == null || correlationId.isBlank()) {
            // Generate new correlation ID (UUID)
            correlationId = UUID.randomUUID().toString();
            log.info("[GW-CorrelationId] Generated | {} | value={}", path, correlationId);
        } else {
            log.info("[GW-CorrelationId] Preserved | {} | value={}", path, correlationId);
        }

        // Step 2: Inject into request headers (propagate to downstream services)
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * Run FIRST in the filter chain (HIGHEST_PRECEDENCE) to ensure
     * X-Correlation-Id is available for all other filters.
     *
     * Execution order:
     * 1. CorrelationIdFilter (this) ← HIGHEST_PRECEDENCE
     * 2. IdempotencyKeyFilter ← HIGHEST_PRECEDENCE + 1
     * 3. Other filters...
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
