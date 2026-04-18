package com.backend.api_gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import com.backend.api_gateway.exception.AppException;
import com.backend.api_gateway.exception.ErrorCode;
import com.backend.api_gateway.service.JwtService;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtAuthFilter Tests")
class JwtAuthFilterTest {

    @Mock
    private JwtService jwtService;

    private JwtAuthFilter jwtAuthFilter;

    @Mock
    private ServerWebExchange exchange;

    @Mock
    private GatewayFilterChain chain;

    @Mock
    private ServerHttpRequest request;

    @BeforeEach
    void setUp() {
        jwtAuthFilter = new JwtAuthFilter(jwtService);
        when(exchange.getRequest()).thenReturn(request);
    }

    @Nested
    @DisplayName("Public paths")
    class PublicPathsTests {

        @Test
        @DisplayName("Should allow access to auth OTP send endpoint")
        void publicPath_authOtpSend() {
            // Given
            when(request.getURI()).thenReturn(URI.create("/api/v1/auth/otp/send"));
            when(chain.filter(exchange)).thenReturn(Mono.empty());

            // When
            var result = jwtAuthFilter.filter(exchange, chain);

            // Then
            StepVerifier.create(result).verifyComplete();
            verify(chain).filter(exchange);
            verify(jwtService, never()).extractRole(any());
        }

        @Test
        @DisplayName("Should allow access to search endpoint")
        void publicPath_search() {
            // Given
            when(request.getURI()).thenReturn(URI.create("/api/v1/search"));
            when(chain.filter(exchange)).thenReturn(Mono.empty());

            // When
            var result = jwtAuthFilter.filter(exchange, chain);

            // Then
            StepVerifier.create(result).verifyComplete();
            verify(chain).filter(exchange);
            verify(jwtService, never()).extractRole(any());
        }

        @Test
        @DisplayName("Should allow access to fallback endpoints")
        void publicPath_fallback() {
            // Given
            when(request.getURI()).thenReturn(URI.create("/fallback/auth"));
            when(chain.filter(exchange)).thenReturn(Mono.empty());

            // When
            var result = jwtAuthFilter.filter(exchange, chain);

            // Then
            StepVerifier.create(result).verifyComplete();
            verify(chain).filter(exchange);
            verify(jwtService, never()).extractRole(any());
        }

        @Test
        @DisplayName("Should allow access to auth register endpoint")
        void publicPath_authRegister() {
            // Given
            when(request.getURI()).thenReturn(URI.create("/api/v1/auth/register"));
            when(chain.filter(exchange)).thenReturn(Mono.empty());

            // When
            var result = jwtAuthFilter.filter(exchange, chain);

            // Then
            StepVerifier.create(result).verifyComplete();
            verify(chain).filter(exchange);
        }
    }

    @Nested
    @DisplayName("Authentication errors")
    class AuthenticationTests {

        @Test
        @DisplayName("Should throw ACCESS_TOKEN_MISSING when no authorization header")
        void authentication_missingToken() {
            // Given
            when(request.getURI()).thenReturn(URI.create("/api/v1/protected"));
            when(request.getHeaders()).thenReturn(new HttpHeaders());

            // When
            var result = jwtAuthFilter.filter(exchange, chain);

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(throwable -> throwable instanceof AppException &&
                            ((AppException) throwable).getErrorCode() == ErrorCode.ACCESS_TOKEN_MISSING)
                    .verify();
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("Should throw ACCESS_TOKEN_MISSING for non-Bearer authorization header")
        void authentication_invalidHeaderFormat() {
            // Given
            when(request.getURI()).thenReturn(URI.create("/api/v1/protected"));
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, "Basic token123");
            when(request.getHeaders()).thenReturn(headers);

            // When
            var result = jwtAuthFilter.filter(exchange, chain);

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(throwable -> throwable instanceof AppException &&
                            ((AppException) throwable).getErrorCode() == ErrorCode.ACCESS_TOKEN_MISSING)
                    .verify();
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("Should throw ACCESS_TOKEN_INVALID when JwtService throws exception")
        void authentication_invalidToken() {
            // Given
            when(request.getURI()).thenReturn(URI.create("/api/v1/protected"));
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer invalid.token");
            when(request.getHeaders()).thenReturn(headers);
            when(jwtService.extractRole("invalid.token"))
                    .thenThrow(new AppException(ErrorCode.ACCESS_TOKEN_INVALID));

            // When
            var result = jwtAuthFilter.filter(exchange, chain);

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(throwable -> throwable instanceof AppException &&
                            ((AppException) throwable).getErrorCode() == ErrorCode.ACCESS_TOKEN_INVALID)
                    .verify();
            verify(chain, never()).filter(any());
        }
    }

    @Nested
    @DisplayName("Authorization - GUEST role")
    class GuestAuthorizationTests {

        @Test
        @DisplayName("Should block GUEST role POST requests")
        void authorization_guestPostRequest() {
            // Given
            when(request.getURI()).thenReturn(URI.create("/api/v1/protected"));
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer guest.token");
            when(request.getHeaders()).thenReturn(headers);
            when(request.getMethod()).thenReturn(HttpMethod.POST);
            when(jwtService.extractRole("guest.token")).thenReturn("GUEST");

            // When
            var result = jwtAuthFilter.filter(exchange, chain);

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(throwable -> throwable instanceof AppException &&
                            ((AppException) throwable).getErrorCode() == ErrorCode.FORBIDDEN)
                    .verify();
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("Should block GUEST role DELETE requests")
        void authorization_guestDeleteRequest() {
            // Given
            when(request.getURI()).thenReturn(URI.create("/api/v1/protected"));
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer guest.token");
            when(request.getHeaders()).thenReturn(headers);
            when(request.getMethod()).thenReturn(HttpMethod.DELETE);
            when(jwtService.extractRole("guest.token")).thenReturn("GUEST");

            // When
            var result = jwtAuthFilter.filter(exchange, chain);

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(throwable -> throwable instanceof AppException &&
                            ((AppException) throwable).getErrorCode() == ErrorCode.FORBIDDEN)
                    .verify();
            verify(chain, never()).filter(any());
        }
    }

    @Test
    @DisplayName("Should have correct filter order")
    void getOrder_returnsCorrectOrder() {
        // When
        int order = jwtAuthFilter.getOrder();

        // Then
        assertThat(order).isEqualTo(Integer.MIN_VALUE + 2);
    }
}