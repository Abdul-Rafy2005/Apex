package com.abdulrafy.backend.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHashingTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void hashPassword_isNotEqualToRaw() {
        String raw = "securePassword123";
        String hashed = encoder.encode(raw);
        assertThat(hashed).isNotEqualTo(raw);
    }

    @Test
    void hashPassword_verifiesCorrectly() {
        String raw = "securePassword123";
        String hashed = encoder.encode(raw);
        assertThat(encoder.matches(raw, hashed)).isTrue();
    }

    @Test
    void hashPassword_rejectsWrongPassword() {
        String raw = "securePassword123";
        String hashed = encoder.encode(raw);
        assertThat(encoder.matches("wrongPassword", hashed)).isFalse();
    }

    @Test
    void hashPassword_differentHashesForSameInput() {
        String raw = "securePassword123";
        String hash1 = encoder.encode(raw);
        String hash2 = encoder.encode(raw);
        assertThat(hash1).isNotEqualTo(hash2);
        assertThat(encoder.matches(raw, hash1)).isTrue();
        assertThat(encoder.matches(raw, hash2)).isTrue();
    }
}
