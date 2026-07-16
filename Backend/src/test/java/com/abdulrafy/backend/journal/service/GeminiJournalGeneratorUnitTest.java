package com.abdulrafy.backend.journal.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiJournalGeneratorUnitTest {

    private final GeminiJournalGenerator generator = new GeminiJournalGenerator(
            "test-key", "gemini-2.5-flash", new tools.jackson.databind.ObjectMapper());

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

    @Test
    void parseResponse_extractsTextFromGeminiFormat() {
        String geminiResponseJson = """
                {
                  "candidates": [{
                    "content": {
                      "parts": [{"text": "Your trading session showed strong discipline with a 60% win rate across 10 trades. The Sharpe ratio of 1.85 indicates solid risk-adjusted returns, though the 8.50% max drawdown suggests room for tighter position sizing. Focus on managing your ETH exposure, which was your weakest performer at -3.20%."}],
                      "role": "model"
                    },
                    "finishReason": "STOP",
                    "index": 0
                  }],
                  "usageMetadata": {
                    "promptTokenCount": 150,
                    "candidatesTokenCount": 65,
                    "totalTokenCount": 215
                  }
                }
                """;

        tools.jackson.databind.ObjectMapper objectMapper = new tools.jackson.databind.ObjectMapper();
        try {
            GeminiJournalGenerator.GeminiResponse response = objectMapper.readValue(
                    geminiResponseJson, GeminiJournalGenerator.GeminiResponse.class);

            String text = response.candidates().get(0).content().parts().get(0).text();

            assertThat(text).contains("60% win rate");
            assertThat(text).contains("Sharpe ratio of 1.85");
            assertThat(text).contains("8.50% max drawdown");
            assertThat(text).contains("ETH");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
