package com.backend.notification_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.backend.notification_service.constants.InternalHeaders;
import com.backend.notification_service.util.CorrelationIdHolder;
import com.backend.notification_service.util.ServiceJwtUtil;

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
            template.header(InternalHeaders.INTERNAL_SECRET, internalSecret);
            template.header(InternalHeaders.SERVICE_JWT, serviceJwtUtil.generateServiceToken());

            String correlationId = CorrelationIdHolder.get();
            if (correlationId != null) {
                template.header(InternalHeaders.CORRELATION_ID, correlationId);
            }
        };
    }
}
