package com.abdulrafy.backend.trading.dto;

import com.abdulrafy.backend.trading.entity.OrderSide;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradeResponse(
    UUID id,
    UUID portfolioId,
    UUID assetId,
    OrderSide side,
    BigDecimal quantity,
    BigDecimal price,
    BigDecimal fee,
    String idempotencyKey,
    Instant executedAt
) {}
