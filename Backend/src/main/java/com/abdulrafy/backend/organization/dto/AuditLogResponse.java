package com.abdulrafy.backend.organization.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
    UUID id,
    UUID userId,
    String userEmail,
    String action,
    UUID targetUserId,
    String targetUserEmail,
    String detail,
    Instant createdAt
) {}
