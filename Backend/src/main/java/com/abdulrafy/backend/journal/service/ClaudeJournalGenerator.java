package com.abdulrafy.backend.journal.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
public class ClaudeJournalGenerator implements AiJournalGenerator {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public ClaudeJournalGenerator(
            @Value("${anthropic.api-key:}") String apiKey,
            @Value("${anthropic.model:claude-3-5-sonnet-20241022}") String model,
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
        String prompt = buildPrompt(metrics);

        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "max_tokens", 300,
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", prompt
                    ))
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.anthropic.com/v1/messages"))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Anthropic API returned status {}: {}", response.statusCode(), response.body());
                throw new RuntimeException("Anthropic API error: " + response.statusCode());
            }

            AnthropicResponse anthropicResponse = objectMapper.readValue(response.body(), AnthropicResponse.class);
            return anthropicResponse.content().get(0).text();

        } catch (Exception e) {
            log.error("Failed to generate journal narrative", e);
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

    record AnthropicResponse(List<ContentBlock> content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentBlock(String text) {}
}
