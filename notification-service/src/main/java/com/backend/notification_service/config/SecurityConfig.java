package com.backend.notification_service.config;

import com.backend.notification_service.constants.InternalHeaders;
import com.backend.notification_service.filter.CorrelationIdFilter;
import com.backend.notification_service.filter.GatewayHeaderFilter;
import com.backend.notification_service.filter.ServiceJwtValidationFilter;
import com.backend.notification_service.security.Http401EntryPoint;
import com.backend.notification_service.security.Http403Handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

        private final GatewayHeaderFilter gatewayHeaderFilter;
        private final ServiceJwtValidationFilter serviceJwtValidationFilter;
        private final CorrelationIdFilter correlationIdFilter;
        private final Http401EntryPoint http401EntryPoint;
        private final Http403Handler http403Handler;

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                return http
                                // Stateless API — no CSRF needed
                                .csrf(AbstractHttpConfigurer::disable)

                                // No session
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                .authorizeHttpRequests(auth -> auth

                                                // Public infrastructure
                                                .requestMatchers("/actuator/**", "/health").permitAll()

                                                // Internal API — permitAll here because protection is handled
                                                // by InternalSecretFilter which sets ROLE_INTERNAL on SecurityContext
                                                .requestMatchers("/internal/**").permitAll()

                                                // Notification read — authenticated users only
                                                .requestMatchers(HttpMethod.GET, "/api/v1/notifications/**")
                                                .hasAnyRole("FARMER", "ADMIN")

                                                // Notification write — authenticated users only
                                                .requestMatchers(HttpMethod.POST, "/api/v1/**")
                                                .hasAnyRole("FARMER", "ADMIN")

                                                .requestMatchers(HttpMethod.PUT, "/api/v1/**")
                                                .hasAnyRole("FARMER", "ADMIN")

                                                .requestMatchers(HttpMethod.PATCH, "/api/v1/**")
                                                .hasAnyRole("FARMER", "ADMIN")

                                                .requestMatchers(HttpMethod.DELETE, "/api/v1/**")
                                                .hasAnyRole("FARMER", "ADMIN")

                                                .anyRequest().authenticated())

                                // JSON error responses
                                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint(http401EntryPoint)
                                                .accessDeniedHandler(http403Handler))

                                // Filter order: correlation → gateway → service JWT → internal secret
                                .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
                                .addFilterAfter(gatewayHeaderFilter, CorrelationIdFilter.class)
                                .addFilterAfter(serviceJwtValidationFilter, GatewayHeaderFilter.class)

                                .build();
        }
}
