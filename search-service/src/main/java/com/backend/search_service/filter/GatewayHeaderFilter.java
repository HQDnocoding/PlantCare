package com.backend.search_service.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Filter to handle authentication details passed from the API Gateway.
 * In a microservices architecture, the Gateway authenticates the request and
 * forwards user identity via HTTP headers.
 */
@Component
@Slf4j
public class GatewayHeaderFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String userId = request.getHeader(USER_ID_HEADER);
        String userRole = request.getHeader(USER_ROLE_HEADER);

        // If headers are present, set security context
        if (userId != null && !userId.isEmpty() && userRole != null && !userRole.isEmpty()) {
            try {
                UUID.fromString(userId); // Validate UUID format

                List<SimpleGrantedAuthority> authorities = Collections
                        .singletonList(new SimpleGrantedAuthority(ROLE_PREFIX + userRole));

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userId, null,
                        authorities);

                SecurityContextHolder.getContext().setAuthentication(auth);

                log.debug("[GatewayHeader] User authenticated via gateway headers: userId={}, role={}",
                        userId, userRole);
            } catch (IllegalArgumentException e) {
                log.warn("[GatewayHeader] Invalid userId UUID format: {}", userId);
            }
        }

        filterChain.doFilter(request, response);
    }
}
