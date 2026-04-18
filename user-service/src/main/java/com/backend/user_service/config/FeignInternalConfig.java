package com.backend.user_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.backend.user_service.util.CorrelationIdHolder;
import com.backend.user_service.util.ServiceJwtUtil;

import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class FeignInternalConfig {

    private final ServiceJwtUtil serviceJwtUtil;

    @Value("${internal.secret}")
    private String internalSecret;

    @Bean
    public RequestInterceptor internalSecretInterceptor() {
        return template -> {
            // Add internal secret header
            // template.header("X-Internal-Secret", internalSecret);

            // Add service-to-service JWT token
            String serviceJwt = serviceJwtUtil.generateServiceToken();
            template.header("X-Service-JWT", serviceJwt);

            // Propagate correlation ID to downstream service-to-service calls
            String correlationId = CorrelationIdHolder.get();
            if (correlationId != null) {
                template.header("X-Correlation-Id", correlationId);
            }
        };
    }
}
