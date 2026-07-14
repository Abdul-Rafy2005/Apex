package com.abdulrafy.backend.analytics.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record AllocationEntry(
    String symbol,
    BigDecimal value,
    BigDecimal pct
) {}
