package com.abdulrafy.backend.auth.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PortfolioSummaryResponse(
    UUID id,
    BigDecimal cashBalance
) {}
