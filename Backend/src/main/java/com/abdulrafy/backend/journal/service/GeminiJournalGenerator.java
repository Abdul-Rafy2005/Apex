package com.abdulrafy.backend.journal.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "apex.journal.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiJournalGenerator implements AiJournalGenerator {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiJournalGenerator(
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.model:gemini-2.5-flash}") String model,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String generateNarrative(JournalMetrics metrics) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new com.abdulrafy.backend.common.exception.ServiceUnavailableException(
                    "Journal generation service is not configured: GEMINI_API_KEY is missing");
        }

        String prompt = buildPrompt(metrics);

        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "contents", List.of(Map.of(
                            "parts", List.of(Map.of("text", prompt))
                    ))
            ));

            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + model + ":generateContent?key=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Gemini API returned status {}: {}", response.statusCode(), response.body());
                throw new RuntimeException("Gemini API error: " + response.statusCode());
            }

            GeminiResponse geminiResponse = objectMapper.readValue(response.body(), GeminiResponse.class);
            return geminiResponse.candidates().get(0).content().parts().get(0).text();

        } catch (Exception e) {
            log.error("Failed to generate journal narrative via Gemini", e);
            throw new RuntimeException("Failed to generate journal narrative: " + e.getMessage(), e);
        }
    }

    String buildPrompt(JournalMetrics metrics) {
        return """
                You are a trading performance analyst. Given the following structured metrics from today's trading session, write a 2-4 sentence behavioral narrative. Focus on patterns, strengths, and areas for improvement. Do NOT invent any numbers not present in the input — only narrate the data provided.

                Today's Metrics:
                - Total trades: %d
                - Winning trades: %d, Losing trades: %d
                - Win rate: %s%%
                - Total return: %s%%
                - Sharpe ratio: %s
                - Max drawdown: %s%%
                - Risk score: %d/100
                - Avg holding period: %s hours
                - Best asset: %s (%s%%)
                - Worst asset: %s (%s%%)
                - Realized P/L: $%s
                - Unrealized P/L: $%s
                - Allocation: %s

                Write a concise, actionable narrative that helps the trader reflect on their performance.
                """.formatted(
                metrics.totalTrades(),
                metrics.winningTrades(),
                metrics.losingTrades(),
                metrics.winRate(),
                metrics.totalReturnPct(),
                metrics.sharpeRatio(),
                metrics.maxDrawdownPct(),
                metrics.riskScore(),
                metrics.avgHoldingPeriodHours(),
                metrics.bestAssetSymbol() != null ? metrics.bestAssetSymbol() : "N/A",
                metrics.bestAssetReturnPct(),
                metrics.worstAssetSymbol() != null ? metrics.worstAssetSymbol() : "N/A",
                metrics.worstAssetReturnPct(),
                metrics.realizedPnl(),
                metrics.unrealizedPnl(),
                metrics.allocationBreakdown() != null ? metrics.allocationBreakdown() : "N/A"
        );
    }

    record GeminiResponse(List<Candidate> candidates) {}

    record Candidate(Content content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(List<Part> parts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Part(String text) {}
}
