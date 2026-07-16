package com.abdulrafy.backend.market.provider;

import com.abdulrafy.backend.market.dto.CoinGeckoMarketChartResponse;
import com.abdulrafy.backend.market.dto.CoinGeckoSimplePriceResponse;
import com.abdulrafy.backend.market.dto.LivePriceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CoinGeckoProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoProvider.class);

    private final WebClient webClient;

    public CoinGeckoProvider(
            @Value("${apex.market.coingecko.base-url:https://api.coingecko.com/api/v3}") String baseUrl,
            @Value("${apex.market.coingecko.api-key:}") String apiKey) {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("x-cg-demo-api-key", apiKey);
        }
        this.webClient = builder.build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, LivePriceResponse> fetchPrices(List<String> providerIds) {
        if (providerIds == null || providerIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String idsParam = String.join(",", providerIds);
        String url = UriComponentsBuilder.fromPath("/simple/price")
                .queryParam("ids", idsParam)
                .queryParam("vs_currencies", "usd")
                .queryParam("include_24hr_change", "true")
                .build()
                .toUriString();

        try {
            Map<String, Object> rawResponse = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (rawResponse == null) {
                return Collections.emptyMap();
            }

            Map<String, LivePriceResponse> result = new HashMap<>();
            Instant now = Instant.now();

            for (String id : providerIds) {
                Object data = rawResponse.get(id);
                if (data instanceof Map<?, ?> map) {
                    Object usdObj = map.get("usd");
                    Object changeObj = map.get("usd_24h_change");
                    if (usdObj instanceof Number usd) {
                        BigDecimal price = BigDecimal.valueOf(usd.doubleValue());
                        BigDecimal change = changeObj instanceof Number c
                                ? BigDecimal.valueOf(c.doubleValue()).setScale(2, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO;
                        result.put(id, new LivePriceResponse(id, price, change, now));
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to fetch prices from CoinGecko for ids={}: {}", idsParam, e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    public CoinGeckoMarketChartResponse fetchHistory(String providerId, int days) {
        String url = UriComponentsBuilder.fromPath("/coins/{id}/market_chart")
                .queryParam("vs_currency", "usd")
                .queryParam("days", days)
                .queryParam("interval", days <= 1 ? "hourly" : "daily")
                .buildAndExpand(providerId)
                .toUriString();

        try {
            return webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(CoinGeckoMarketChartResponse.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();
        } catch (Exception e) {
            log.error("Failed to fetch history from CoinGecko for id={}: {}", providerId, e.getMessage());
            return new CoinGeckoMarketChartResponse(List.of(), List.of());
        }
    }
    @Override
    public List<com.abdulrafy.backend.market.dto.GlobalAssetResponse> searchGlobalAssets(String query) {
        String url = UriComponentsBuilder.fromPath("/search")
                .queryParam("query", query)
                .build()
                .toUriString();

        try {
            Map<String, Object> rawResponse = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (rawResponse == null || !rawResponse.containsKey("coins")) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> coins = (List<Map<String, Object>>) rawResponse.get("coins");
            return coins.stream()
                    .map(c -> new com.abdulrafy.backend.market.dto.GlobalAssetResponse(
                            (String) c.get("id"),
                            (String) c.get("symbol"),
                            (String) c.get("name"),
                            (String) c.get("thumb")
                    ))
                    .toList();
        } catch (Exception e) {
            log.error("Failed to search global assets from CoinGecko for query={}: {}", query, e.getMessage());
            return Collections.emptyList();
        }
    }
}
