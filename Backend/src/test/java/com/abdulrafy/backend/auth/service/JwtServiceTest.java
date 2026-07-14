package com.abdulrafy.backend.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private final UUID testUserId = UUID.randomUUID();
    private final String testEmail = "test@example.com";
    private final String testRole = "TRADER";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
            "dGhpcy1pcy1hLXZlcnktbG9uZy1zZWN1cmUtc2VjcmV0LWtleS1mb3Itand0",
            900000L,
            604800000L
        );
    }

    @Test
    void generateAccessToken_returnsNonEmptyToken() {
        String token = jwtService.generateAccessToken(testUserId, testEmail, testRole);
        assertThat(token).isNotBlank();
    }

    @Test
    void generateRefreshToken_returnsNonEmptyToken() {
        String token = jwtService.generateRefreshToken(testUserId, testEmail, testRole);
        assertThat(token).isNotBlank();
    }

    @Test
    void parseToken_extractsCorrectUserId() {
        String token = jwtService.generateAccessToken(testUserId, testEmail, testRole);
        UUID extracted = jwtService.extractUserId(token);
        assertThat(extracted).isEqualTo(testUserId);
    }

    @Test
    void parseToken_extractsCorrectEmail() {
        String token = jwtService.generateAccessToken(testUserId, testEmail, testRole);
        String extracted = jwtService.extractEmail(token);
        assertThat(extracted).isEqualTo(testEmail);
    }

    @Test
    void parseToken_extractsCorrectRole() {
        String token = jwtService.generateAccessToken(testUserId, testEmail, testRole);
        String extracted = jwtService.extractRole(token);
        assertThat(extracted).isEqualTo(testRole);
    }

    @Test
    void isTokenValid_returnsTrueForValidToken() {
        String token = jwtService.generateAccessToken(testUserId, testEmail, testRole);
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_returnsFalseForTamperedToken() {
        String token = jwtService.generateAccessToken(testUserId, testEmail, testRole);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    void extractUserId_throwsForInvalidToken() {
        org.junit.jupiter.api.Assertions.assertThrows(
            Exception.class,
            () -> jwtService.extractUserId("invalid.token.here")
        );
    }
}
