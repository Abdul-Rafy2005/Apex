package com.abdulrafy.backend.organization.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LeaderboardResponse(
    UUID userId,
    String displayName,
    BigDecimal totalReturnPct,
    BigDecimal portfolioValue,
    int rank
) {}
