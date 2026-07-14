package com.abdulrafy.backend.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AnalyticsHistoryResponse(
    List<SnapshotPoint> history
) {
    public record SnapshotPoint(
        LocalDate date,
        BigDecimal totalReturnPct,
        BigDecimal portfolioValue,
        BigDecimal dailyPnl,
        BigDecimal sharpeRatio,
        BigDecimal maxDrawdownPct
    ) {}
}
