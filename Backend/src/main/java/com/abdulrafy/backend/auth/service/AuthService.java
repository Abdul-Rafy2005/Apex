package com.abdulrafy.backend.auth.service;

import com.abdulrafy.backend.auth.dto.*;
import com.abdulrafy.backend.auth.entity.Portfolio;
import com.abdulrafy.backend.auth.entity.User;
import com.abdulrafy.backend.auth.entity.UserRole;
import com.abdulrafy.backend.auth.mapper.PortfolioMapper;
import com.abdulrafy.backend.auth.mapper.UserMapper;
import com.abdulrafy.backend.auth.repository.PortfolioRepository;
import com.abdulrafy.backend.auth.repository.UserRepository;
import com.abdulrafy.backend.common.exception.ConflictException;
import com.abdulrafy.backend.common.exception.NotFoundException;
import com.abdulrafy.backend.common.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final BigDecimal seedBalance;

    public AuthService(
            UserRepository userRepository,
            PortfolioRepository portfolioRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${apex.auth.seed-balance:100000}") BigDecimal seedBalance) {
        this.userRepository = userRepository;
        this.portfolioRepository = portfolioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.seedBalance = seedBalance;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .role(UserRole.TRADER)
                .build();
        user = userRepository.save(user);

        Portfolio portfolio = Portfolio.builder()
                .user(user)
                .cashBalance(seedBalance)
                .build();
        portfolioRepository.save(portfolio);

        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshTokenRequest request) {
        if (!jwtService.isTokenValid(request.refreshToken())) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        UUID userId = jwtService.extractUserId(request.refreshToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Portfolio portfolio = portfolioRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Portfolio not found"));

        return new UserProfileResponse(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getRole(),
            user.getCreatedAt(),
            PortfolioMapper.toSummaryResponse(portfolio)
        );
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(
            user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(
            user.getId(), user.getEmail(), user.getRole().name());
        return new AuthResponse(accessToken, refreshToken, UserMapper.toResponse(user));
    }
}
