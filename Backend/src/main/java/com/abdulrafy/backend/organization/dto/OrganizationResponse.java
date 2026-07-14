package com.abdulrafy.backend.organization.dto;

import com.abdulrafy.backend.auth.entity.UserRole;
import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(
    UUID id,
    String name,
    String type,
    UUID createdBy,
    Instant createdAt
) {}
