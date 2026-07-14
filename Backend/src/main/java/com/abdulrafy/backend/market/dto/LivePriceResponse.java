package com.abdulrafy.backend.market.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record LivePriceResponse(
    String symbol,
    BigDecimal priceUsd,
    BigDecimal change24hPct,
    Instant timestamp
) {}
