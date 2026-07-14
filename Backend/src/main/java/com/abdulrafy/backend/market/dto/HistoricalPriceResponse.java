package com.abdulrafy.backend.market.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record HistoricalPriceResponse(
    Instant time,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal volume
) {}
