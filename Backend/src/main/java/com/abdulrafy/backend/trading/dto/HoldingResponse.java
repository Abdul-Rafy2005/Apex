package com.abdulrafy.backend.trading.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record HoldingResponse(
    UUID assetId,
    String symbol,
    String name,
    BigDecimal quantity,
    BigDecimal avgEntryPrice,
    BigDecimal currentPrice,
    BigDecimal unrealizedPnl
) {}
