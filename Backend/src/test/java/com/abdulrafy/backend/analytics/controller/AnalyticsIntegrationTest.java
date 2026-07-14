package com.abdulrafy.backend.analytics.controller;

import com.abdulrafy.backend.IntegrationTestBase;
import com.abdulrafy.backend.analytics.entity.PerformanceSnapshot;
import com.abdulrafy.backend.analytics.repository.PerformanceSnapshotRepository;
import com.abdulrafy.backend.auth.dto.AuthResponse;
import com.abdulrafy.backend.auth.dto.RegisterRequest;
import com.abdulrafy.backend.auth.entity.Portfolio;
import com.abdulrafy.backend.auth.repository.PortfolioRepository;
import com.abdulrafy.backend.market.entity.Asset;
import com.abdulrafy.backend.market.repository.AssetRepository;
import com.abdulrafy.backend.market.dto.LivePriceResponse;
import com.abdulrafy.backend.market.provider.MarketDataProvider;
import com.abdulrafy.backend.trading.dto.ExecuteTradeRequest;
import com.abdulrafy.backend.trading.entity.OrderSide;
import com.abdulrafy.backend.trading.repository.HoldingRepository;
import com.abdulrafy.backend.trading.repository.TradeRepository;
import com.abdulrafy.backend.analytics.service.AnalyticsService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class AnalyticsIntegrationTest extends IntegrationTestBase {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private AssetRepository assetRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private PerformanceSnapshotRepository snapshotRepository;
    @Autowired private AnalyticsService analyticsService;
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
        snapshotRepository.deleteAllInBatch();

        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });

        // Create asset
        assetRepository.deleteAllInBatch();
        btcAsset = Asset.builder()
                .symbol("BTC").name("Bitcoin").precision(8)
                .providerSource("bitcoin").tradable(true).build();
        assetRepository.saveAndFlush(btcAsset);

        // Mock market data — BTC at 42000
        when(marketDataProvider.fetchPrices(anyList())).thenReturn(Map.of(
                "bitcoin", new LivePriceResponse("BTC", new BigDecimal("42000"), BigDecimal.ZERO, Instant.now())
        ));

        // Register user
        String email = "analytics-" + UUID.randomUUID() + "@test.com";
        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest(email, "password123", "Analytics Test"))))
                .andExpect(status().isCreated())
                .andReturn();

        authResponse = objectMapper.readValue(
                regResult.getResponse().getContentAsString(), AuthResponse.class);

        // Get portfolio ID
        String meBody = mockMvc.perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var profile = objectMapper.readValue(meBody,
                com.abdulrafy.backend.auth.dto.UserProfileResponse.class);
        portfolioId = profile.portfolio().id();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test: Execute a trade, then manually trigger snapshot, verify API
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void tradeTriggersSnapshot_analyticsSummaryReflectsIt() throws Exception {
        // Buy 1 BTC at 42000, fee = 42
        ExecuteTradeRequest buyReq = new ExecuteTradeRequest(
                btcAsset.getId(), OrderSide.BUY, new BigDecimal("1"), "idem-analytics-1");
        mockMvc.perform(post("/api/v1/trading/execute")
                .header("Authorization", "Bearer " + authResponse.accessToken())
                .header("Idempotency-Key", "idem-analytics-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buyReq)))
                .andExpect(status().isCreated());

        // Wait for the AnalyticsConsumer to process the event and create a snapshot
        // Poll with timeout since the consumer runs asynchronously
        PerformanceSnapshot snapshot = null;
        for (int i = 0; i < 50; i++) {
            Thread.sleep(100);
            snapshot = snapshotRepository.findTopByPortfolioIdOrderBySnapshotDateDesc(portfolioId).orElse(null);
            if (snapshot != null) break;
        }

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getPortfolioId()).isEqualTo(portfolioId);
        assertThat(snapshot.getTotalTrades()).isEqualTo(1);
        assertThat(snapshot.getBestAssetSymbol()).isEqualTo("BTC");

        // Verify API returns the snapshot
        mockMvc.perform(get("/api/v1/analytics/summary")
                .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTrades").value(1))
                .andExpect(jsonPath("$.bestAssetSymbol").value("BTC"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test: Analytics summary endpoint works (returns default when no trades)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void analyticsSummary_noTrades_returnsDefault() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/summary")
                .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTrades").value(0))
                .andExpect(jsonPath("$.portfolioValue").value(100000));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test: Analytics history endpoint works
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void analyticsHistory_noSnapshots_returnsEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/history")
                .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history").isArray())
                .andExpect(jsonPath("$.history.length()").value(0));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test: Cross-tenant isolation — User A cannot see User B's analytics
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void crossTenant_userACannotSeeUserBAnalytics() throws Exception {
        // Execute a trade as User A
        ExecuteTradeRequest buyReq = new ExecuteTradeRequest(
                btcAsset.getId(), OrderSide.BUY, new BigDecimal("1"), "idem-cross-a");
        mockMvc.perform(post("/api/v1/trading/execute")
                .header("Authorization", "Bearer " + authResponse.accessToken())
                .header("Idempotency-Key", "idem-cross-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buyReq)))
                .andExpect(status().isCreated());

        analyticsService.recomputeSnapshot(portfolioId);

        // Register User B
        String emailB = "analytics-b-" + UUID.randomUUID() + "@test.com";
        MvcResult regB = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest(emailB, "password123", "User B"))))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authB = objectMapper.readValue(
                regB.getResponse().getContentAsString(), AuthResponse.class);

        // User B's analytics should be empty (no trades)
        String bodyB = mockMvc.perform(get("/api/v1/analytics/summary")
                .header("Authorization", "Bearer " + authB.accessToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var summaryB = objectMapper.readValue(bodyB,
                com.abdulrafy.backend.analytics.dto.AnalyticsSummaryResponse.class);
        assertThat(summaryB.totalTrades()).isZero();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test: Unauthenticated request is rejected
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void analyticsSummary_unauthenticated_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/summary"))
                .andExpect(status().isForbidden());
    }
}
