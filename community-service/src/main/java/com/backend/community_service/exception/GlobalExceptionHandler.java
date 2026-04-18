package com.backend.community_service.exception;

import com.backend.community_service.dto.ApiResponse;

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

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        @ExceptionHandler(AppException.class)
        public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
                log.warn("Business error [{}]: {}", ex.getErrorCode().name(), ex.getMessage());
                return ResponseEntity
                                .status(ex.getHttpStatus())
                                .body(ApiResponse.error(ex.getErrorCode().name(), ex.getMessage()));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
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

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ApiResponse<Void>> handleMalformedJson(HttpMessageNotReadableException ex) {
                log.warn("Malformed request body: {}", ex.getMessage());
                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error("INVALID_REQUEST_BODY",
                                                "Request body is missing or malformed."));
        }

        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
                String message = ex.getConstraintViolations().stream()
                                .map(ConstraintViolation::getMessage)
                                .collect(Collectors.joining(", "));
                log.warn("Constraint violation: {}", message);
                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error("VALIDATION_ERROR", message));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
                log.error("Unexpected error: {}", ex.getMessage(), ex);
                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ApiResponse.error(
                                                ErrorCode.INTERNAL_ERROR.name(),
                                                ErrorCode.INTERNAL_ERROR.getMessage()));
        }
}