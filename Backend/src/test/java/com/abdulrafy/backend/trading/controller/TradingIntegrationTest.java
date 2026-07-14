package com.abdulrafy.backend.trading.controller;

import com.abdulrafy.backend.IntegrationTestBase;
import com.abdulrafy.backend.auth.dto.RegisterRequest;
import com.abdulrafy.backend.auth.dto.AuthResponse;
import com.abdulrafy.backend.market.entity.Asset;
import com.abdulrafy.backend.market.repository.AssetRepository;
import com.abdulrafy.backend.market.dto.LivePriceResponse;
import com.abdulrafy.backend.market.provider.MarketDataProvider;
import com.abdulrafy.backend.trading.dto.ExecuteTradeRequest;
import com.abdulrafy.backend.trading.entity.OrderSide;
import com.abdulrafy.backend.trading.entity.Trade;
import com.abdulrafy.backend.trading.repository.HoldingRepository;
import com.abdulrafy.backend.trading.repository.TradeRepository;
import com.abdulrafy.backend.auth.entity.Portfolio;
import com.abdulrafy.backend.auth.repository.PortfolioRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class TradingIntegrationTest extends IntegrationTestBase {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private AssetRepository assetRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private MarketDataProvider marketDataProvider;

    private MockMvc mockMvc;
    private Asset btcAsset;
    private AuthResponse authResponse;
    private UUID portfolioId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();

        // Clean up
        tradeRepository.deleteAllInBatch();
        holdingRepository.deleteAllInBatch();

        // Clear Redis cache
        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });

        // Create asset
        assetRepository.deleteAllInBatch();
        btcAsset = Asset.builder()
                .symbol("BTC")
                .name("Bitcoin")
                .precision(8)
                .providerSource("bitcoin")
                .tradable(true)
                .build();
        assetRepository.saveAndFlush(btcAsset);

        // Mock market data
        when(marketDataProvider.fetchPrices(anyList())).thenReturn(Map.of(
                "bitcoin", new LivePriceResponse("BTC", new BigDecimal("42000"), BigDecimal.ZERO, Instant.now())
        ));

        // Register user
        String uniqueEmail = "trader-" + UUID.randomUUID() + "@test.com";
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest(uniqueEmail, "password123", "Test Trader"))))
                .andExpect(status().isCreated())
                .andReturn();

        authResponse = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(), AuthResponse.class);

        // Get portfolio ID
        mockMvc.perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolio.id").isNotEmpty());

        // Find portfolio by looking up the user's portfolio
        // We need to extract portfolioId from the response
        String meBody = mockMvc.perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        com.abdulrafy.backend.auth.dto.UserProfileResponse profile =
                objectMapper.readValue(meBody, com.abdulrafy.backend.auth.dto.UserProfileResponse.class);
        portfolioId = profile.portfolio().id();
    }

    @Test
    void executeTrade_buy_updatesPortfolioAndHoldings() throws Exception {
        ExecuteTradeRequest request = new ExecuteTradeRequest(
                btcAsset.getId(), OrderSide.BUY, new BigDecimal("1"), "idem-buy-1");

        mockMvc.perform(post("/api/v1/trading/execute")
                .header("Authorization", "Bearer " + authResponse.accessToken())
                .header("Idempotency-Key", "idem-buy-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.quantity").value(1))
                .andExpect(jsonPath("$.price").value(42000));

        // Check portfolio balance
        mockMvc.perform(get("/api/v1/trading/portfolio")
                .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashBalance").value(57958))
                .andExpect(jsonPath("$.holdings.length()").value(1))
                .andExpect(jsonPath("$.holdings[0].symbol").value("BTC"))
                .andExpect(jsonPath("$.holdings[0].quantity").value(1));
    }

    @Test
    void executeTrade_sell_afterBuy_works() throws Exception {
        // First buy
        ExecuteTradeRequest buyReq = new ExecuteTradeRequest(
                btcAsset.getId(), OrderSide.BUY, new BigDecimal("2"), "idem-sell-buy");
        mockMvc.perform(post("/api/v1/trading/execute")
                .header("Authorization", "Bearer " + authResponse.accessToken())
                .header("Idempotency-Key", "idem-sell-buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buyReq)))
                .andExpect(status().isCreated());

        // Then sell 1
        ExecuteTradeRequest sellReq = new ExecuteTradeRequest(
                btcAsset.getId(), OrderSide.SELL, new BigDecimal("1"), "idem-sell-sell");
        mockMvc.perform(post("/api/v1/trading/execute")
                .header("Authorization", "Bearer " + authResponse.accessToken())
                .header("Idempotency-Key", "idem-sell-sell")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sellReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.side").value("SELL"));

        // Verify: bought 2 at 42000, sold 1 at 42000
        // Cost: 2*42000 + fee(84) = 84084. Cash left: 100000 - 84084 = 15916
        // Proceeds: 1*42000 - fee(42) = 41958. Cash now: 15916 + 41958 = 57874
        mockMvc.perform(get("/api/v1/trading/portfolio")
                .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashBalance").value(57874))
                .andExpect(jsonPath("$.holdings.length()").value(1))
                .andExpect(jsonPath("$.holdings[0].quantity").value(1));
    }

    @Test
    void executeTrade_idempotencyKeyReplay_returnsSameTrade() throws Exception {
        ExecuteTradeRequest request = new ExecuteTradeRequest(
                btcAsset.getId(), OrderSide.BUY, new BigDecimal("1"), "idem-replay");

        // First call
        MvcResult result1 = mockMvc.perform(post("/api/v1/trading/execute")
                .header("Authorization", "Bearer " + authResponse.accessToken())
                .header("Idempotency-Key", "idem-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        // Second call with same key
        MvcResult result2 = mockMvc.perform(post("/api/v1/trading/execute")
                .header("Authorization", "Bearer " + authResponse.accessToken())
                .header("Idempotency-Key", "idem-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        // Same trade ID returned
        com.abdulrafy.backend.trading.dto.TradeResponse trade1 =
                objectMapper.readValue(result1.getResponse().getContentAsString(),
                        com.abdulrafy.backend.trading.dto.TradeResponse.class);
        com.abdulrafy.backend.trading.dto.TradeResponse trade2 =
                objectMapper.readValue(result2.getResponse().getContentAsString(),
                        com.abdulrafy.backend.trading.dto.TradeResponse.class);

        assertThat(trade1.id()).isEqualTo(trade2.id());

        // Only one trade in the database
        assertThat(tradeRepository.count()).isEqualTo(1);
    }

    @Test
    void executeTrade_insufficientFunds_rejected() throws Exception {
        // Try to buy 3 BTC: 3 * 42000 = 126000 > 100000
        ExecuteTradeRequest request = new ExecuteTradeRequest(
                btcAsset.getId(), OrderSide.BUY, new BigDecimal("3"), "idem-nofunds");

        mockMvc.perform(post("/api/v1/trading/execute")
                .header("Authorization", "Bearer " + authResponse.accessToken())
                .header("Idempotency-Key", "idem-nofunds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Insufficient funds")));
    }

    @Test
    void executeTrade_noAsset_rejected() throws Exception {
        ExecuteTradeRequest request = new ExecuteTradeRequest(
                UUID.randomUUID(), OrderSide.BUY, new BigDecimal("1"), "idem-noasset");

        mockMvc.perform(post("/api/v1/trading/execute")
                .header("Authorization", "Bearer " + authResponse.accessToken())
                .header("Idempotency-Key", "idem-noasset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTrades_returnsTradeHistory() throws Exception {
        // Execute a trade first
        ExecuteTradeRequest request = new ExecuteTradeRequest(
                btcAsset.getId(), OrderSide.BUY, new BigDecimal("1"), "idem-history");
        mockMvc.perform(post("/api/v1/trading/execute")
                .header("Authorization", "Bearer " + authResponse.accessToken())
                .header("Idempotency-Key", "idem-history")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Get trade history
        mockMvc.perform(get("/api/v1/trading/trades")
                .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].side").value("BUY"));
    }

    @Test
    void concurrency_twoConcurrentBuys_noOverdraft() throws Exception {
        // Cash starts at 100000. Buy 1 BTC at 42000 + fee 42 = 42042.
        // Cash after setup buy: 100000 - 42042 = 57958
        ExecuteTradeRequest setupBuy = new ExecuteTradeRequest(
                btcAsset.getId(), OrderSide.BUY, new BigDecimal("1"), "idem-conc-setup");
        mockMvc.perform(post("/api/v1/trading/execute")
                .header("Authorization", "Bearer " + authResponse.accessToken())
                .header("Idempotency-Key", "idem-conc-setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(setupBuy)))
                .andExpect(status().isCreated());

        // Cash = 57958. Two concurrent BUYS for 1 BTC (cost 42042 each).
        // Only one can succeed. The other gets 409 Conflict (optimistic lock).

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        List<MvcResult> results = new CopyOnWriteArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    String key = "idem-conc-" + UUID.randomUUID();
                    ExecuteTradeRequest req = new ExecuteTradeRequest(
                            btcAsset.getId(), OrderSide.BUY, new BigDecimal("1"), key);

                    MvcResult result = mockMvc.perform(post("/api/v1/trading/execute")
                            .header("Authorization", "Bearer " + authResponse.accessToken())
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                            .andReturn();
                    results.add(result);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 1. Exactly one succeeded (201) and exactly one failed (409)
        assertThat(results).hasSize(2);
        long successCount = results.stream()
                .filter(r -> r.getResponse().getStatus() == 201)
                .count();
        long conflictCount = results.stream()
                .filter(r -> r.getResponse().getStatus() == 409)
                .count();
        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(1);

        // 2. Final cash balance is exactly 57958 - 42042 = 15916
        MvcResult portfolioResult = mockMvc.perform(get("/api/v1/trading/portfolio")
                .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andReturn();

        String portfolioBody = portfolioResult.getResponse().getContentAsString();
        BigDecimal finalCash = new BigDecimal(
                objectMapper.readTree(portfolioBody).get("cashBalance").asText());
        assertThat(finalCash).isEqualByComparingTo(new BigDecimal("15916"));

        // 3. Holdings quantity is exactly 2 (1 from setup buy + 1 from successful concurrent buy)
        var holdings = objectMapper.readTree(portfolioBody).get("holdings");
        assertThat(holdings.isArray()).isTrue();
        assertThat(holdings.size()).isEqualTo(1);
        assertThat(holdings.get(0).get("quantity").asDouble()).isEqualTo(2.0);
    }
}
