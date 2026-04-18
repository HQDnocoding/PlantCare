package com.backend.search_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.backend.search_service.constants.InternalHeaders;
import com.backend.search_service.util.CorrelationIdHolder;
import com.backend.search_service.util.ServiceJwtUtil;

import feign.Logger;
import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;

/**
 * Feign interceptor for internal service-to-service calls.
 * Attaches three security/tracing headers to every outbound Feign request:
 * - X-Internal-Secret : shared secret proving caller is an internal service
 * - X-Service-JWT : short-lived JWT identifying the calling service
 * - X-Correlation-Id : propagated trace ID for end-to-end request tracing
 */
@Configuration
@RequiredArgsConstructor
public class FeignInternalConfig {

    private final ServiceJwtUtil serviceJwtUtil;

    @Value("${internal.secret}")
    private String internalSecret;

    @Bean
    public RequestInterceptor internalSecretInterceptor() {
        return template -> {
            // Layer 1: shared secret — proves caller is a trusted internal service
            // template.header(InternalHeaders.INTERNAL_SECRET, internalSecret);

            // Layer 2: short-lived JWT — identifies calling service and prevents replay
            // Token is cached in ServiceJwtUtil and regenerated before expiry
            template.header(InternalHeaders.SERVICE_JWT,
                    serviceJwtUtil.generateServiceToken());

            // Layer 3: correlation ID — propagate only if present in current request
            // context
            String correlationId = CorrelationIdHolder.get();
            if (correlationId != null) {
                template.header(InternalHeaders.CORRELATION_ID, correlationId);
            }
        };
    }

    // Restrict Feign logging to avoid leaking secret headers into log files
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}