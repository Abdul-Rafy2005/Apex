package com.abdulrafy.backend.organization.dto;

import com.abdulrafy.backend.auth.entity.UserRole;
import java.time.Instant;
import java.util.UUID;

public record OrgDetailResponse(
    UUID id,
    String name,
    String type,
    UUID createdBy,
    Instant createdAt,
    UserRole currentUserRole,
    int memberCount
) {}
