package com.abdulrafy.backend.analytics.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AnalyticsSummaryResponse(
    LocalDate snapshotDate,

    // Return metrics
    BigDecimal totalReturnPct,
    BigDecimal dailyReturnPct,

    // Trade metrics
    int totalTrades,
    int winningTrades,
    int losingTrades,
    BigDecimal winRate,
    BigDecimal avgWinPct,
    BigDecimal avgLossPct,
    BigDecimal largestGainPct,
    BigDecimal largestLossPct,

    // Risk metrics
    BigDecimal sharpeRatio,
    BigDecimal maxDrawdownPct,
    BigDecimal avgHoldingPeriodHours,
    int riskScore,

    // Portfolio values
    BigDecimal portfolioValue,
    BigDecimal cashBalance,
    BigDecimal investedValue,
    BigDecimal realizedPnl,
    BigDecimal unrealizedPnl,

    // Best/worst asset
    String bestAssetSymbol,
    BigDecimal bestAssetReturnPct,
    String worstAssetSymbol,
    BigDecimal worstAssetReturnPct,

    // Breakdowns
    List<AllocationEntry> allocationBreakdown,
    List<DailyPnlEntry> dailyPnlSeries
) {}
