package com.abdulrafy.backend.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApexException.class)
    public ResponseEntity<Map<String, Object>> handleApexException(ApexException ex) {
        Map<String, Object> body = Map.of(
            "type", URI.create("https://api.apex.com/errors/" + ex.getType()),
            "title", ex.getType(),
            "status", ex.getStatus(),
            "detail", ex.getMessage(),
            "instance", URI.create("/error"),
            "timestamp", Instant.now().toString()
        );
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> Map.of("field", fe.getField(), "message", fe.getDefaultMessage()))
            .toList();
        Map<String, Object> body = Map.of(
            "type", URI.create("https://api.apex.com/errors/validation"),
            "title", "validation-error",
            "status", 400,
            "detail", "Validation failed",
            "errors", errors,
            "timestamp", Instant.now().toString()
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        Map<String, Object> body = Map.of(
            "type", URI.create("https://api.apex.com/errors/forbidden"),
            "title", "forbidden",
            "status", 403,
            "detail", "Access denied",
            "timestamp", Instant.now().toString()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        Map<String, Object> body = Map.of(
            "type", URI.create("https://api.apex.com/errors/unauthorized"),
            "title", "unauthorized",
            "status", 401,
            "detail", "Authentication required",
            "timestamp", Instant.now().toString()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        Map<String, Object> body = Map.of(
            "type", URI.create("https://api.apex.com/errors/internal"),
            "title", "internal-error",
            "status", 500,
            "detail", "An unexpected error occurred",
            "timestamp", Instant.now().toString()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
