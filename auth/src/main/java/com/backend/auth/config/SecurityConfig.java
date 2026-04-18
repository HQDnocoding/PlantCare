package com.backend.auth.config;

import com.backend.auth.constants.InternalHeaders;
import com.backend.auth.filter.CorrelationIdFilter;
import com.backend.auth.filter.GatewayHeaderFilter;
import com.backend.auth.filter.ServiceJwtValidationFilter;
import com.backend.auth.security.Http401EntryPoint;
import com.backend.auth.security.Http403Handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
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
        public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
                return config.getAuthenticationManager();
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                return http
                                // JWT is header-based — no session or CSRF cookie involved
                                .csrf(AbstractHttpConfigurer::disable)

                                // No session
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                .authorizeHttpRequests(auth -> auth

                                                // Public infrastructure
                                                .requestMatchers("/actuator/**", "/health").permitAll()

                                                // Public auth endpoints — login, register, refresh
                                                .requestMatchers("/api/v1/auth/**").permitAll()

                                                // Internal API — permitAll here because protection is handled
                                                // by InternalSecretFilter which sets ROLE_INTERNAL on SecurityContext
                                                .requestMatchers("/internal/**").permitAll()

                                                // Admin-only endpoints
                                                .requestMatchers(HttpMethod.GET, "/api/v1/admin/**").hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.POST, "/api/v1/admin/**").hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.PUT, "/api/v1/admin/**").hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/**").hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/**").hasRole("ADMIN")

                                                .anyRequest().authenticated())

                                // JSON error responses
                                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint(http401EntryPoint)
                                                .accessDeniedHandler(http403Handler))

                                // Filter order: correlation → gateway → service JWT
                                .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
                                .addFilterAfter(gatewayHeaderFilter, CorrelationIdFilter.class)
                                .addFilterAfter(serviceJwtValidationFilter, GatewayHeaderFilter.class)

                                .build();
        }
}