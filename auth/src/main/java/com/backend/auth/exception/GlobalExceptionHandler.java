package com.backend.auth.exception;

import com.backend.auth.domain.dto.response.ApiResponse;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;
import java.util.stream.Stream;

// @RestControllerAdvice intercepts exceptions thrown by any @RestController.
// Instead of Spring returning its default error format, we return our ApiResponse.
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

        // Handle our own business logic exceptions
        @ExceptionHandler(AppException.class)
        public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
                // Log as WARN — these are expected business errors, not system failures
                log.warn("Business error [{}] - {}: {}",
                                ex.getErrorCode().name(),
                                ex.getClass().getSimpleName(),
                                ex.getMessage());

                return ResponseEntity
                                .status(ex.getHttpStatus())
                                .body(ApiResponse.error(ex.getErrorCode().name(), ex.getMessage()));
        }

        // Handle @Valid / @Validated failures on request DTOs.
        // Spring throws this when e.g. phone format is wrong, required field missing.
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<Void>> handleValidationException(
                        MethodArgumentNotValidException ex) {

                // Collect all field errors into one message: "phone: Invalid format, code:
                // Required"
                String message = Stream.concat(
                                ex.getBindingResult().getFieldErrors().stream()
                                                .map(FieldError::getDefaultMessage),
                                ex.getBindingResult().getGlobalErrors().stream()
                                                .map(ObjectError::getDefaultMessage))
                                .collect(Collectors.joining(", "));

                log.warn("Validation error: {}", message);

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error("VALIDATION_ERROR", message));
        }

        // Handle Facebook signature validation failures
        @ExceptionHandler(FacebookSignatureInvalidException.class)
        public ResponseEntity<ApiResponse<Void>> handleFacebookSignatureInvalid(
                        FacebookSignatureInvalidException ex) {
                log.warn("Facebook signature validation failed: {}", ex.getMessage());
                return ResponseEntity
                                .status(HttpStatus.UNAUTHORIZED)
                                .body(ApiResponse.error("INVALID_FACEBOOK_SIGNATURE",
                                                "Request signature verification failed. Only Facebook can call this endpoint."));
        }

        // Catch-all for unexpected exceptions — never expose stack traces to client
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception ex) {
                if (ex instanceof AppException) {
                        return handleAppException((AppException) ex);
                }
                log.error("Unexpected error: {}", ex.getMessage(), ex);

                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ApiResponse.error(
                                                ErrorCode.INTERNAL_SERVER_ERROR.name(),
                                                ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ApiResponse<Void>> handleMalformedJson(
                        HttpMessageNotReadableException ex) {
                log.warn("Malformed request body: {}", ex.getMessage());
                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error("INVALID_REQUEST_BODY",
                                                "Request body is missing or malformed."));
        }

        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
                        ConstraintViolationException ex) {
                String message = ex.getConstraintViolations().stream()
                                .map(ConstraintViolation::getMessage)
                                .collect(Collectors.joining(", "));
                log.warn("Constraint violation: {}", message);
                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error("VALIDATION_ERROR", message));
        }
}