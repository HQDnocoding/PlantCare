package com.backend.auth.filter;

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
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String userIdStr = request.getHeader(USER_ID_HEADER);
        String roleHeader = request.getHeader(USER_ROLE_HEADER);

        if (userIdStr != null && !userIdStr.isBlank()) {
            try {
                UUID userId = UUID.fromString(userIdStr);
                List<SimpleGrantedAuthority> authorities = Collections.emptyList();

                // Process role and ensure it follows Spring Security's ROLE_ convention
                if (roleHeader != null && !roleHeader.isBlank()) {
                    String formattedRole = roleHeader.toUpperCase().startsWith(ROLE_PREFIX)
                            ? roleHeader.toUpperCase()
                            : ROLE_PREFIX + roleHeader.toUpperCase();

                    authorities = Collections.singletonList(new SimpleGrantedAuthority(formattedRole));
                }

                // Create authentication object and set it in the SecurityContext
                // Principal is set as the UUID (userId), credentials as null
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userId, null,
                        authorities);

                SecurityContextHolder.getContext().setAuthentication(auth);

                log.debug("Internal SecurityContext populated for User: {} with Roles: {}", userId, authorities);

            } catch (IllegalArgumentException e) {
                log.warn("Security bypass: Invalid UUID format in {} header: {}", USER_ID_HEADER, userIdStr);
                // We don't throw an exception here; Spring Security will treat this as an
                // anonymous request
            }
        }

        chain.doFilter(request, response);
    }
}