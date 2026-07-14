package com.abdulrafy.backend.market.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AssetResponse(
    UUID id,
    String symbol,
    String name,
    Integer precision,
    String providerSource,
    Boolean tradable,
    Instant createdAt
) {}
