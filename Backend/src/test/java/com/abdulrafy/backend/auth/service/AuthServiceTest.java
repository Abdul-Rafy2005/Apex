package com.abdulrafy.backend.auth.service;

import com.abdulrafy.backend.auth.dto.*;
import com.abdulrafy.backend.auth.entity.Portfolio;
import com.abdulrafy.backend.auth.entity.User;
import com.abdulrafy.backend.auth.entity.UserRole;
import com.abdulrafy.backend.auth.repository.PortfolioRepository;
import com.abdulrafy.backend.auth.repository.UserRepository;
import com.abdulrafy.backend.common.exception.ConflictException;
import com.abdulrafy.backend.common.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, portfolioRepository, passwordEncoder, jwtService,
            new BigDecimal("100000"));
    }

    @Test
    void register_createsUserAndPortfolio() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(inv -> {
            Portfolio p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        when(jwtService.generateAccessToken(any(), any(), any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any(), any(), any())).thenReturn("refresh-token");

        AuthService.TokenPair response = authService.register(
            new RegisterRequest("test@example.com", "password123", "Test User"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().email()).isEqualTo("test@example.com");
        verify(portfolioRepository).save(argThat(p ->
            p.getCashBalance().compareTo(new BigDecimal("100000")) == 0));
    }

    @Test
    void register_rejectsDuplicateEmail() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
            new RegisterRequest("test@example.com", "password123", "Test User")))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already registered");
    }

    @Test
    void login_validCredentials_returnsTokens() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .passwordHash("hashed")
                .displayName("Test")
                .role(UserRole.TRADER)
                .build();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(any(), any(), any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any(), any(), any())).thenReturn("refresh-token");

        AuthService.TokenPair response = authService.login(new LoginRequest("test@example.com", "password123"));

        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .passwordHash("hashed")
                .role(UserRole.TRADER)
                .build();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("test@example.com", "wrong")))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_unknownEmail_throwsUnauthorized() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("unknown@example.com", "pass")))
            .isInstanceOf(UnauthorizedException.class);
    }
}
