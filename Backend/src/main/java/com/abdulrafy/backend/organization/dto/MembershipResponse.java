package com.abdulrafy.backend.organization.dto;

import com.abdulrafy.backend.auth.entity.UserRole;
import java.time.Instant;
import java.util.UUID;

public record MembershipResponse(
    UUID id,
    UUID userId,
    String userEmail,
    String userDisplayName,
    UserRole role,
    Instant createdAt
) {}
