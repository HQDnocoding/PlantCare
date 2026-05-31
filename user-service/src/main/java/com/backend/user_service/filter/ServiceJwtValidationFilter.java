package com.backend.user_service.filter;

import com.backend.user_service.util.ServiceJwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ServiceJwtValidationFilter extends OncePerRequestFilter {

    private final ServiceJwtUtil serviceJwtUtil;
    private final ObjectMapper objectMapper;

    @Value("${security.inter-service.enabled:true}")
    private boolean interServiceSecurityEnabled;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Chỉ validate các endpoint nội bộ
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!interServiceSecurityEnabled) {
            log.warn("Inter-service auth is DISABLED — skipping JWT validation");
            filterChain.doFilter(request, response);
            return;
        }

        String serviceJwt = request.getHeader("X-Service-JWT");

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

        request.setAttribute("X-Caller-Service", callerService);
        filterChain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Unauthorized");
        errorResponse.put("message", message);
        errorResponse.put("status", HttpStatus.UNAUTHORIZED.value());

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        response.getWriter().flush();
    }
}