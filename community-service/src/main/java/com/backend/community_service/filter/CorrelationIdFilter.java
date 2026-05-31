package com.backend.community_service.filter;

import com.backend.community_service.util.CorrelationIdHolder;
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

        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {

            correlationId = UUID.randomUUID().toString().substring(0, 8);
        }

        try {
            CorrelationIdHolder.set(correlationId);

            MDC.put(MDC_KEY, correlationId);

            response.setHeader(CORRELATION_ID_HEADER, correlationId);

            log.debug("Tracing request: {} {} | ID: {}",
                    request.getMethod(), request.getRequestURI(), correlationId);

            filterChain.doFilter(request, response);

        } finally {

            MDC.remove(MDC_KEY);
            CorrelationIdHolder.clear();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}