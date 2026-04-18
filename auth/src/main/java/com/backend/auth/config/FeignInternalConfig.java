package com.backend.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.backend.auth.constants.InternalHeaders;
import com.backend.auth.util.CorrelationIdHolder;
import com.backend.auth.util.ServiceJwtUtil;

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

    @Bean
    public RequestInterceptor internalSecretInterceptor() {
        return template -> {

            template.header(InternalHeaders.SERVICE_JWT,
                    serviceJwtUtil.generateServiceToken());

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