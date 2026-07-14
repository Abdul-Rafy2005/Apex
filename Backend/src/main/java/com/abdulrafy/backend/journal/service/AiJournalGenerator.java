package com.abdulrafy.backend.journal.service;

import java.math.BigDecimal;
import java.util.Map;

public interface AiJournalGenerator {

    /**
     * Generates a behavioral narrative from the day's trade summary.
     * The LLM only narrates pre-validated metrics — it never invents figures.
     *
     * @param metrics structured payload of the day's trading activity
     * @return natural-language narrative (2-4 sentences)
     */
    String generateNarrative(JournalMetrics metrics);

    record JournalMetrics(
            int totalTrades,
            int winningTrades,
            int losingTrades,
            BigDecimal winRate,
            BigDecimal totalReturnPct,
            BigDecimal sharpeRatio,
            BigDecimal maxDrawdownPct,
            int riskScore,
            BigDecimal avgHoldingPeriodHours,
            String bestAssetSymbol,
            BigDecimal bestAssetReturnPct,
            String worstAssetSymbol,
            BigDecimal worstAssetReturnPct,
            BigDecimal realizedPnl,
            BigDecimal unrealizedPnl,
            Map<String, BigDecimal> allocationBreakdown
    ) {}
}
