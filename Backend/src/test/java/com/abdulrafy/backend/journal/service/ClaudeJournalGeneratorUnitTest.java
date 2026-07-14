package com.abdulrafy.backend.journal.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeJournalGeneratorUnitTest {

    private final ClaudeJournalGenerator generator = new ClaudeJournalGenerator(
            "test-key", "test-model", new tools.jackson.databind.ObjectMapper());

    @Test
    void buildPrompt_containsAllMetricsFields() {
        AiJournalGenerator.JournalMetrics metrics = new AiJournalGenerator.JournalMetrics(
                10, 6, 4,
                new BigDecimal("60.00"),
                new BigDecimal("5.25"),
                new BigDecimal("1.85"),
                new BigDecimal("8.50"),
                42,
                new BigDecimal("4.50"),
                "BTC",
                new BigDecimal("12.30"),
                "ETH",
                new BigDecimal("-3.20"),
                new BigDecimal("1500.00"),
                new BigDecimal("320.50"),
                Map.of("BTC", new BigDecimal("6000.00"), "ETH", new BigDecimal("4000.00"))
        );

        String prompt = generator.buildPrompt(metrics);

        // Verify all metrics are present in the prompt
        assertThat(prompt).contains("Total trades: 10");
        assertThat(prompt).contains("Winning trades: 6");
        assertThat(prompt).contains("Losing trades: 4");
        assertThat(prompt).contains("Win rate: 60.00%");
        assertThat(prompt).contains("Total return: 5.25%");
        assertThat(prompt).contains("Sharpe ratio: 1.85");
        assertThat(prompt).contains("Max drawdown: 8.50%");
        assertThat(prompt).contains("Risk score: 42/100");
        assertThat(prompt).contains("Avg holding period: 4.50 hours");
        assertThat(prompt).contains("Best asset: BTC (12.30%)");
        assertThat(prompt).contains("Worst asset: ETH (-3.20%)");
        assertThat(prompt).contains("Realized P/L: $1500.00");
        assertThat(prompt).contains("Unrealized P/L: $320.50");
        assertThat(prompt).contains("Allocation:");
    }

    @Test
    void buildPrompt_handlesNullSymbols() {
        AiJournalGenerator.JournalMetrics metrics = new AiJournalGenerator.JournalMetrics(
                0, 0, 0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                null,
                BigDecimal.ZERO,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Map.of()
        );

        String prompt = generator.buildPrompt(metrics);

        assertThat(prompt).contains("Best asset: N/A");
        assertThat(prompt).contains("Worst asset: N/A");
        assertThat(prompt).contains("Allocation: {}");
    }

    @Test
    void buildPrompt_instructsModelNotToInventNumbers() {
        AiJournalGenerator.JournalMetrics metrics = new AiJournalGenerator.JournalMetrics(
                5, 3, 2,
                new BigDecimal("60.00"),
                new BigDecimal("2.10"),
                new BigDecimal("1.20"),
                new BigDecimal("5.00"),
                30,
                new BigDecimal("2.00"),
                "BTC",
                new BigDecimal("8.50"),
                "ETH",
                new BigDecimal("-1.50"),
                new BigDecimal("500.00"),
                new BigDecimal("150.00"),
                Map.of("BTC", new BigDecimal("3000.00"))
        );

        String prompt = generator.buildPrompt(metrics);

        assertThat(prompt).containsIgnoringCase("Do NOT invent any numbers not present in the input");
    }
}
