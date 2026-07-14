package com.abdulrafy.backend.analytics.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyPnlEntry(
    LocalDate date,
    BigDecimal pnl
) {}
