package com.abdulrafy.backend.trading.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PortfolioResponse(
    UUID id,
    BigDecimal cashBalance,
    List<HoldingResponse> holdings,
    BigDecimal totalUnrealizedPnl
) {}
