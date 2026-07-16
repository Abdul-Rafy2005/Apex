package com.abdulrafy.backend.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    String type,
    String title,
    String body,
    UUID referenceId,
    String referenceType,
    Instant readAt,
    Instant createdAt
) {}
