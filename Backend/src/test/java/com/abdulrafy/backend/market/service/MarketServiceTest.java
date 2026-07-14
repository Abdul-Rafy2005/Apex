package com.abdulrafy.backend.market.service;

import com.abdulrafy.backend.common.exception.NotFoundException;
import com.abdulrafy.backend.market.dto.*;
import com.abdulrafy.backend.market.entity.Asset;
import com.abdulrafy.backend.market.provider.MarketDataProvider;
import com.abdulrafy.backend.market.repository.AssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketServiceTest {

    @Mock
    private AssetRepository assetRepository;
    @Mock
    private MarketDataProvider marketDataProvider;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private MarketService marketService;

    private static Asset buildAsset(String symbol, String providerSource) {
        return Asset.builder()
                .id(UUID.randomUUID())
                .symbol(symbol)
                .name(symbol + " Token")
                .precision(8)
                .providerSource(providerSource)
                .tradable(true)
                .build();
    }

    @BeforeEach
    void setUp() {
        marketService = new MarketService(assetRepository, marketDataProvider, redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void listAssets_returnsMappedResponses() {
        Asset btc = buildAsset("BTC", "bitcoin");
        Asset eth = buildAsset("ETH", "ethereum");
        when(assetRepository.findByTradableTrue()).thenReturn(List.of(btc, eth));

        List<AssetResponse> result = marketService.listAssets();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).symbol()).isEqualTo("BTC");
        assertThat(result.get(1).symbol()).isEqualTo("ETH");
    }

    @Test
    void fetchLivePrices_cachesFreshPrices() {
        Asset btc = buildAsset("BTC", "bitcoin");
        when(assetRepository.findBySymbolIn(List.of("BTC"))).thenReturn(List.of(btc));

        LivePriceResponse providerPrice = new LivePriceResponse(
                "bitcoin", new BigDecimal("42000"), new BigDecimal("2.5"), Instant.now());
        when(marketDataProvider.fetchPrices(List.of("bitcoin")))
                .thenReturn(Map.of("bitcoin", providerPrice));
        when(valueOperations.get("market:price:BTC")).thenReturn(null);
        when(valueOperations.get("market:change24h:BTC")).thenReturn(null);

        List<LivePriceResponse> result = marketService.fetchLivePrices(List.of("BTC"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).symbol()).isEqualTo("BTC");
        assertThat(result.get(0).priceUsd()).isEqualByComparingTo(new BigDecimal("42000"));
        verify(valueOperations).set(eq("market:price:BTC"), eq("42000"), any(Duration.class));
        verify(valueOperations).set(eq("market:change24h:BTC"), eq("2.5"), any(Duration.class));
    }

    @Test
    void fetchLivePrices_providerFails_fallsBackToCache() {
        Asset btc = buildAsset("BTC", "bitcoin");
        when(assetRepository.findBySymbolIn(List.of("BTC"))).thenReturn(List.of(btc));
        when(marketDataProvider.fetchPrices(List.of("bitcoin"))).thenReturn(Collections.emptyMap());
        when(valueOperations.get("market:price:BTC")).thenReturn("41500");
        when(valueOperations.get("market:change24h:BTC")).thenReturn("1.2");

        List<LivePriceResponse> result = marketService.fetchLivePrices(List.of("BTC"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).symbol()).isEqualTo("BTC");
        assertThat(result.get(0).priceUsd()).isEqualByComparingTo(new BigDecimal("41500"));
        assertThat(result.get(0).change24hPct()).isEqualByComparingTo(new BigDecimal("1.2"));
    }

    @Test
    void fetchLivePrices_noCacheAndProviderFails_returnsEmpty() {
        Asset btc = buildAsset("BTC", "bitcoin");
        when(assetRepository.findBySymbolIn(List.of("BTC"))).thenReturn(List.of(btc));
        when(marketDataProvider.fetchPrices(List.of("bitcoin"))).thenReturn(Collections.emptyMap());
        when(valueOperations.get("market:price:BTC")).thenReturn(null);

        List<LivePriceResponse> result = marketService.fetchLivePrices(List.of("BTC"));

        assertThat(result).isEmpty();
    }

    @Test
    void getHistory_nonExistentSymbol_throwsNotFound() {
        when(assetRepository.findBySymbol("FAKE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marketService.getHistory("FAKE", 30))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getHistory_existingSymbol_returnsHistoricalPrices() {
        Asset btc = buildAsset("BTC", "bitcoin");
        when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btc));

        CoinGeckoMarketChartResponse chart = new CoinGeckoMarketChartResponse(
                List.of(
                    List.of(1700000000000.0, 42000.0),
                    List.of(1700000060000.0, 42500.0)
                ),
                List.of(
                    List.of(1700000000000.0, 1000000.0),
                    List.of(1700000060000.0, 1200000.0)
                )
        );
        when(marketDataProvider.fetchHistory("bitcoin", 30)).thenReturn(chart);

        List<HistoricalPriceResponse> result = marketService.getHistory("BTC", 30);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).close()).isEqualByComparingTo(new BigDecimal("42000"));
        assertThat(result.get(1).close()).isEqualByComparingTo(new BigDecimal("42500"));
    }
}
