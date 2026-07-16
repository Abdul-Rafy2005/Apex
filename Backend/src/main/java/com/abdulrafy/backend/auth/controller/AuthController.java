package com.abdulrafy.backend.auth.controller;

import com.abdulrafy.backend.auth.dto.*;
import com.abdulrafy.backend.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Registration, login, and token refresh")
public class AuthController {

    private final AuthService authService;
    private final long refreshTokenMaxAgeSeconds;

    public AuthController(
            AuthService authService,
            @Value("${apex.auth.jwt-refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
        this.authService = authService;
        this.refreshTokenMaxAgeSeconds = refreshTokenExpirationMs / 1000;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user account")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        AuthService.TokenPair tokens = authService.register(request);
        addRefreshTokenCookie(response, tokens.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(tokens.accessToken(), tokens.user()));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with email and password")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthService.TokenPair tokens = authService.login(request);
        addRefreshTokenCookie(response, tokens.refreshToken());
        return ResponseEntity.ok(new AuthResponse(tokens.accessToken(), tokens.user()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token cookie")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(value = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AuthService.TokenPair tokens = authService.refresh(refreshToken);
        addRefreshTokenCookie(response, tokens.refreshToken());
        return ResponseEntity.ok(new AuthResponse(tokens.accessToken(), tokens.user()));
    }

    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/api/v1/auth")
                .maxAge(refreshTokenMaxAgeSeconds)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
