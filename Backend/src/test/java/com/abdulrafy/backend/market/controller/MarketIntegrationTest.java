package com.abdulrafy.backend.market.controller;

import com.abdulrafy.backend.IntegrationTestBase;
import com.abdulrafy.backend.market.dto.CoinGeckoMarketChartResponse;
import com.abdulrafy.backend.market.dto.LivePriceResponse;
import com.abdulrafy.backend.market.entity.Asset;
import com.abdulrafy.backend.market.provider.MarketDataProvider;
import com.abdulrafy.backend.market.repository.AssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class MarketIntegrationTest extends IntegrationTestBase {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private MarketDataProvider marketDataProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        assetRepository.deleteAllInBatch();
        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });

        Asset btc = Asset.builder()
                .symbol("BTC")
                .name("Bitcoin")
                .precision(8)
                .providerSource("bitcoin")
                .tradable(true)
                .build();

        Asset eth = Asset.builder()
                .symbol("ETH")
                .name("Ethereum")
                .precision(8)
                .providerSource("ethereum")
                .tradable(true)
                .build();

        assetRepository.saveAndFlush(btc);
        assetRepository.saveAndFlush(eth);
    }

    @Test
    void listAssets_returnsTradableAssets() throws Exception {
        mockMvc.perform(get("/api/v1/market/assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].symbol").isNotEmpty())
                .andExpect(jsonPath("$[0].providerSource").isNotEmpty());
    }

    @Test
    void getPrices_withProviderData_returnsPrices() throws Exception {
        when(marketDataProvider.fetchPrices(anyList())).thenReturn(Map.of(
                "bitcoin", new LivePriceResponse("BTC", new BigDecimal("42000"), new BigDecimal("2.5"), Instant.now()),
                "ethereum", new LivePriceResponse("ETH", new BigDecimal("2200"), new BigDecimal("-1.2"), Instant.now())
        ));

        mockMvc.perform(get("/api/v1/market/prices").param("symbols", "BTC,ETH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getPrices_providerFails_returnsEmpty() throws Exception {
        when(marketDataProvider.fetchPrices(anyList())).thenReturn(Map.of());

        mockMvc.perform(get("/api/v1/market/prices").param("symbols", "BTC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getOverview_returnsMarketOverview() throws Exception {
        when(marketDataProvider.fetchPrices(anyList())).thenReturn(Map.of(
                "bitcoin", new LivePriceResponse("BTC", new BigDecimal("42000"), new BigDecimal("5.0"), Instant.now()),
                "ethereum", new LivePriceResponse("ETH", new BigDecimal("2200"), new BigDecimal("-3.0"), Instant.now())
        ));

        mockMvc.perform(get("/api/v1/market/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAssets").value(2))
                .andExpect(jsonPath("$.topGainers").isArray())
                .andExpect(jsonPath("$.topLosers").isArray())
                .andExpect(jsonPath("$.trending").isArray());
    }

    @Test
    void getHistory_nonExistentSymbol_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/market/FAKE/history").param("days", "7"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getHistory_existingSymbol_returnsHistory() throws Exception {
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
        when(marketDataProvider.fetchHistory(anyString(), anyInt())).thenReturn(chart);

        mockMvc.perform(get("/api/v1/market/BTC/history").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].close").value(42000))
                .andExpect(jsonPath("$[1].close").value(42500));
    }

    @Test
    void getHistory_defaultDays_is30() throws Exception {
        CoinGeckoMarketChartResponse chart = new CoinGeckoMarketChartResponse(List.of(), List.of());
        when(marketDataProvider.fetchHistory(anyString(), anyInt())).thenReturn(chart);

        mockMvc.perform(get("/api/v1/market/BTC/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
