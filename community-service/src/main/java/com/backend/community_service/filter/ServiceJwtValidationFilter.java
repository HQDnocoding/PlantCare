package com.backend.community_service.filter;

import com.backend.community_service.constants.InternalHeaders;
import com.backend.community_service.dto.ApiResponse;
import com.backend.community_service.util.ServiceJwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Service-to-Service JWT Validation Filter
 * 
 * Validates incoming requests from other microservices
 * Ensures the request is from a trusted service by validating:
 * 1. X-Internal-Secret header matches
 * 2. X-Service-JWT token is valid and not expired
 */
/**
 * Validates Service JWT on inbound internal service-to-service requests.
 * Only applies to /internal/** endpoints.
 * X-Internal-Secret is validated separately by InternalSecretFilter.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ServiceJwtValidationFilter extends OncePerRequestFilter {

    private final ServiceJwtUtil serviceJwtUtil;
    private final ObjectMapper objectMapper;

    @Value("${security.inter-service.enabled:true}")
    private boolean interServiceAuthEnabled;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only validate service JWT for internal service-to-service endpoints
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!interServiceAuthEnabled) {
            log.warn("Inter-service auth is DISABLED — skipping JWT validation");
            filterChain.doFilter(request, response);
            return;
        }

        String serviceJwt = request.getHeader(InternalHeaders.SERVICE_JWT);

        if (serviceJwt == null || serviceJwt.isBlank()) {
            log.warn("Missing X-Service-JWT | path={}", request.getRequestURI());
            sendError(response, "Missing service JWT token");
            return;
        }

        String callerService = serviceJwtUtil.validateServiceToken(serviceJwt);
        if (callerService == null) {
            log.warn("Invalid or expired X-Service-JWT | path={}", request.getRequestURI());
            sendError(response, "Invalid or expired service JWT token");
            return;
        }

        log.debug("Service-to-service request authenticated | caller={} path={}",
                callerService, request.getRequestURI());

        // Make caller identity available to downstream handlers
        request.setAttribute("X-Caller-Service", callerService);
        filterChain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                objectMapper.writeValueAsString(
                        ApiResponse.error("UNAUTHORIZED", message)));
    }
}