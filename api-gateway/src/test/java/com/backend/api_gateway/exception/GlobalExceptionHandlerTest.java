package com.backend.api_gateway.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import com.backend.api_gateway.dto.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    @Mock
    private ServerWebExchange exchange;

    @Mock
    private org.springframework.http.server.reactive.ServerHttpResponse response;

    @Mock
    private org.springframework.http.HttpHeaders responseHeaders;

    @BeforeEach
    void setUp() {
        when(exchange.getResponse()).thenReturn(response);
        when(response.getHeaders()).thenReturn(responseHeaders);
        when(response.bufferFactory()).thenReturn(mock(org.springframework.core.io.buffer.DataBufferFactory.class));
    }

    @Nested
    @DisplayName("AppException handling")
    class AppExceptionHandlingTests {

        @Test
        @DisplayName("Should handle ACCESS_TOKEN_INVALID AppException")
        void handle_appException_accessTokenInvalid() throws Exception {
            // Given
            AppException appException = new AppException(ErrorCode.ACCESS_TOKEN_INVALID);
            when(objectMapper.writeValueAsBytes(any(ApiResponse.class)))
                    .thenReturn(
                            "{\"success\":false,\"errorCode\":\"ACCESS_TOKEN_INVALID\",\"message\":\"Access token is invalid or expired.\"}"
                                    .getBytes());

            var buffer = mock(org.springframework.core.io.buffer.DataBuffer.class);
            when(response.bufferFactory().wrap(any(byte[].class))).thenReturn(buffer);
            when(response.writeWith(any(Mono.class))).thenReturn(Mono.empty());

            // When
            var result = globalExceptionHandler.handle(exchange, appException);

            // Then
            StepVerifier.create(result)
                    .verifyComplete();
            verify(responseHeaders).setContentType(MediaType.APPLICATION_JSON);
            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
            verify(objectMapper).writeValueAsBytes(any(ApiResponse.class));
        }

        @Test
        @DisplayName("Should handle FORBIDDEN AppException")
        void handle_appException_forbidden() throws Exception {
            // Given
            AppException appException = new AppException(ErrorCode.FORBIDDEN);
            when(objectMapper.writeValueAsBytes(any(ApiResponse.class)))
                    .thenReturn(
                            "{\"success\":false,\"errorCode\":\"FORBIDDEN\",\"message\":\"You do not have permission to access this resource.\"}"
                                    .getBytes());

            var buffer = mock(org.springframework.core.io.buffer.DataBuffer.class);
            when(response.bufferFactory().wrap(any(byte[].class))).thenReturn(buffer);
            when(response.writeWith(any(Mono.class))).thenReturn(Mono.empty());

            // When
            var result = globalExceptionHandler.handle(exchange, appException);

            // Then
            StepVerifier.create(result)
                    .verifyComplete();
            verify(responseHeaders).setContentType(MediaType.APPLICATION_JSON);
            verify(response).setStatusCode(HttpStatus.FORBIDDEN);
            verify(objectMapper).writeValueAsBytes(any(ApiResponse.class));
        }
    }

    @Nested
    @DisplayName("ResponseStatusException handling")
    class ResponseStatusExceptionHandlingTests {

        @Test
        @DisplayName("Should handle 404 ResponseStatusException")
        void handle_responseStatusException_notFound() throws Exception {
            // Given
            ResponseStatusException rsException = new ResponseStatusException(HttpStatus.NOT_FOUND, "Not Found");
            when(objectMapper.writeValueAsBytes(any(ApiResponse.class)))
                    .thenReturn(
                            "{\"success\":false,\"errorCode\":\"GATEWAY_ERROR\",\"message\":\"Not Found\"}".getBytes());

            var buffer = mock(org.springframework.core.io.buffer.DataBuffer.class);
            when(response.bufferFactory().wrap(any(byte[].class))).thenReturn(buffer);
            when(response.writeWith(any(Mono.class))).thenReturn(Mono.empty());

            // When
            var result = globalExceptionHandler.handle(exchange, rsException);

            // Then
            StepVerifier.create(result)
                    .verifyComplete();
            verify(responseHeaders).setContentType(MediaType.APPLICATION_JSON);
            verify(response).setStatusCode(HttpStatus.NOT_FOUND);
            verify(objectMapper).writeValueAsBytes(any(ApiResponse.class));
        }
    }

    @Nested
    @DisplayName("Generic exception handling")
    class GenericExceptionHandlingTests {

        @Test
        @DisplayName("Should handle unexpected RuntimeException")
        void handle_genericException_runtimeException() throws Exception {
            // Given
            RuntimeException genericException = new RuntimeException("Unexpected error");
            when(objectMapper.writeValueAsBytes(any(ApiResponse.class)))
                    .thenReturn(
                            "{\"success\":false,\"errorCode\":\"INTERNAL_SERVER_ERROR\",\"message\":\"An unexpected error occurred. Please try again later.\"}"
                                    .getBytes());

            var buffer = mock(org.springframework.core.io.buffer.DataBuffer.class);
            when(response.bufferFactory().wrap(any(byte[].class))).thenReturn(buffer);
            when(response.writeWith(any(Mono.class))).thenReturn(Mono.empty());

            // When
            var result = globalExceptionHandler.handle(exchange, genericException);

            // Then
            StepVerifier.create(result)
                    .verifyComplete();
            verify(responseHeaders).setContentType(MediaType.APPLICATION_JSON);
            verify(response).setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            verify(objectMapper).writeValueAsBytes(any(ApiResponse.class));
        }

        @Test
        @DisplayName("Should handle JSON processing error during response serialization")
        void handle_jsonProcessingException() throws Exception {
            // Given
            RuntimeException genericException = new RuntimeException("Unexpected error");
            when(objectMapper.writeValueAsBytes(any(ApiResponse.class)))
                    .thenThrow(new JsonProcessingException("Serialization failed") {
                    });

            when(response.setComplete()).thenReturn(Mono.empty());

            // When
            var result = globalExceptionHandler.handle(exchange, genericException);

            // Then
            StepVerifier.create(result)
                    .verifyComplete();
            verify(responseHeaders).setContentType(MediaType.APPLICATION_JSON);
            verify(response).setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            verify(response).setComplete();
        }
    }
}