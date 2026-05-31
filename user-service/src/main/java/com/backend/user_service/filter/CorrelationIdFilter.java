package com.backend.user_service.filter;

import com.backend.user_service.util.CorrelationIdHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

@Component
@Slf4j
public class CorrelationIdFilter extends OncePerRequestFilter implements Ordered {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Extract correlation ID from header or generate
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString().substring(0, 8);
        }

        try {
            // 2. Set in ThreadLocal for Feign interceptors
            CorrelationIdHolder.set(correlationId);

            // 3. Set in MDC for logging (all logs from this thread will include it)
            MDC.put(MDC_KEY, correlationId);

            log.debug("Request started | correlationId={} | {} {}",
                    correlationId, request.getMethod(), request.getRequestURI());

            filterChain.doFilter(request, response);

        } finally {
            MDC.remove(MDC_KEY);
            CorrelationIdHolder.clear();
        }
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
