package com.backend.api_gateway.exception;

import com.backend.api_gateway.dto.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Order(-1)
@Component
@RequiredArgsConstructor
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        var response = exchange.getResponse();
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> body;
        HttpStatus status;

        if (ex instanceof AppException appEx) {
            // Known business error — use its status and error code
            status = appEx.getErrorCode().getHttpStatus();
            body = ApiResponse.error(
                    appEx.getErrorCode().name(),
                    appEx.getMessage());
            log.warn("Business error [{}]: {}", appEx.getErrorCode().name(), appEx.getMessage());

        } else if (ex instanceof ResponseStatusException rsEx) {
            // Spring internal errors (404, 405, etc.)
            status = HttpStatus.valueOf(rsEx.getStatusCode().value());
            body = ApiResponse.error(
                    "GATEWAY_ERROR",
                    rsEx.getReason() != null ? rsEx.getReason() : rsEx.getMessage());
            log.warn("Response status error: {}", rsEx.getMessage());

        } else {
            // Unexpected error — never expose internal details to client
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            body = ApiResponse.error(
                    ErrorCode.INTERNAL_SERVER_ERROR.name(),
                    ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
            log.error("Unexpected gateway error: {}", ex.getMessage(), ex);
        }

        response.setStatusCode(status);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            var buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response", e);
            return response.setComplete();
        }
    }
}
