package com.abdulrafy.backend.auth.dto;

import com.abdulrafy.backend.auth.entity.UserRole;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String displayName,
    UserRole role,
    Instant createdAt
) {}
