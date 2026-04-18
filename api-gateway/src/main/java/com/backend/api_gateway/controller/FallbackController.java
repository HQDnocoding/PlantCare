package com.backend.api_gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backend.api_gateway.dto.FallbackResponse;

import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Handles fallback responses when a circuit breaker is open.
 *
 * Circuit breaker states:
 * CLOSED - service healthy, requests pass through normally
 * OPEN - service failing, gateway returns fallback immediately
 * HALF-OPEN - gateway sends test requests to check recovery
 *
 * Each downstream service has its own endpoint so clients
 * receive a meaningful errorCode instead of a generic 503.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

        // Uses @RequestMapping (not @GetMapping) to accept all HTTP methods,
        // since circuit breaker may forward GET, POST, PUT, etc. to the same fallback.

        @RequestMapping("/auth")
        public Mono<ResponseEntity<FallbackResponse>> authFallback() {
                return buildFallback("AUTH_SERVICE_UNAVAILABLE",
                                "Auth service is temporarily unavailable. Please try again later.");
        }

        // chat-service is heavier (RAG + CNN + Gemini) — more likely to be slow or down
        @RequestMapping("/chat")
        public Mono<ResponseEntity<FallbackResponse>> chatFallback() {
                return buildFallback("CHAT_SERVICE_UNAVAILABLE",
                                "Chat service is temporarily unavailable. Please try again later.");
        }

        // Diseases endpoint (shares chat-service circuit breaker for mobile sync)
        @RequestMapping("/diseases")
        public Mono<ResponseEntity<FallbackResponse>> diseasesFallback() {
                return buildFallback("DISEASE_DATA_UNAVAILABLE",
                                "Disease data is temporarily unavailable. Please check your cached data.");
        }

        @RequestMapping("/scan")
        public Mono<ResponseEntity<FallbackResponse>> scanFallback() {
                return buildFallback("SCAN_SERVICE_UNAVAILABLE",
                                "Scan service is temporarily unavailable. Please try again later.");
        }

        @RequestMapping("/user")
        public Mono<ResponseEntity<FallbackResponse>> userFallback() {
                return buildFallback("USER_SERVICE_UNAVAILABLE",
                                "User service is temporarily unavailable. Please try again later.");
        }

        @RequestMapping("/community")
        public Mono<ResponseEntity<FallbackResponse>> communityFallback() {
                return buildFallback("COMMUNITY_SERVICE_UNAVAILABLE",
                                "Community service is temporarily unavailable. Please try again later.");
        }

        @RequestMapping("/search")
        public Mono<ResponseEntity<FallbackResponse>> searchFallback() {
                return buildFallback("SEARCH_SERVICE_UNAVAILABLE",
                                "Search service is temporarily unavailable. Please try again later.");
        }

        @RequestMapping("/notification")
        public Mono<ResponseEntity<FallbackResponse>> notificationFallback() {
                return buildFallback("NOTIFICATION_SERVICE_UNAVAILABLE",
                                "Notification service is temporarily unavailable.");
        }

        // Builds a 503 response with a structured body.
        // Mono.just() wraps an already-computed value — no async needed here.
        private Mono<ResponseEntity<FallbackResponse>> buildFallback(
                        String errorCode, String message) {
                return Mono.just(ResponseEntity
                                .status(HttpStatus.SERVICE_UNAVAILABLE)
                                .body(new FallbackResponse(
                                                false,
                                                errorCode,
                                                message,
                                                Instant.now().toString()))); // ISO-8601 UTC timestamp
        }
}