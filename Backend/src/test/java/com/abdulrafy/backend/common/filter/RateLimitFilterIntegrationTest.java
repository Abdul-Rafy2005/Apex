package com.abdulrafy.backend.common.filter;

import com.abdulrafy.backend.IntegrationTestBase;
import com.abdulrafy.backend.auth.dto.RegisterRequest;
import com.abdulrafy.backend.auth.dto.AuthResponse;
import com.abdulrafy.backend.market.entity.Asset;
import com.abdulrafy.backend.market.repository.AssetRepository;
import com.abdulrafy.backend.market.dto.LivePriceResponse;
import com.abdulrafy.backend.market.provider.MarketDataProvider;
import com.abdulrafy.backend.trading.dto.ExecuteTradeRequest;
import com.abdulrafy.backend.trading.entity.OrderSide;
import com.abdulrafy.backend.trading.repository.HoldingRepository;
import com.abdulrafy.backend.trading.repository.OrderRepository;
import com.abdulrafy.backend.trading.repository.TradeRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestPropertySource(properties = {
        "apex.ratelimit.register=2",
        "apex.ratelimit.login=2",
        "apex.ratelimit.trade=2",
        "apex.ratelimit.journal=2",
        "apex.ratelimit.window-seconds=60"
})
class RateLimitFilterIntegrationTest extends IntegrationTestBase {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AssetRepository assetRepository;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private OrderRepository orderRepository;

    @MockitoBean private MarketDataProvider marketDataProvider;

    private MockMvc mockMvc;
    private Asset btcAsset;

    private static final String TEST_IP = "10.0.0.1";

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });

        tradeRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        holdingRepository.deleteAllInBatch();
        assetRepository.deleteAllInBatch();

        btcAsset = Asset.builder()
                .symbol("BTC")
                .name("Bitcoin")
                .precision(8)
                .providerSource("bitcoin")
                .tradable(true)
                .build();
        assetRepository.saveAndFlush(btcAsset);

        when(marketDataProvider.fetchPrices(anyList())).thenReturn(Map.of(
                "bitcoin", new LivePriceResponse("BTC", new BigDecimal("42000"), BigDecimal.ZERO, Instant.now())
        ));
    }

    private AuthResponse registerUser(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest(email, "password123", "Test User"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
    }

    @Test
    void register_withinLimit_succeeds() throws Exception {
        String email1 = "rate1-" + UUID.randomUUID() + "@test.com";
        String email2 = "rate2-" + UUID.randomUUID() + "@test.com";

        mockMvc.perform(post("/api/v1/auth/register")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest(email1, "password123", "User1"))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-RateLimit-Limit"))
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(header().string("X-RateLimit-Remaining", "1"));

        mockMvc.perform(post("/api/v1/auth/register")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest(email2, "password123", "User2"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-RateLimit-Remaining", "0"));
    }

    @Test
    void register_exceedsLimit_returns429() throws Exception {
        String email1 = "rate-a-" + UUID.randomUUID() + "@test.com";
        String email2 = "rate-b-" + UUID.randomUUID() + "@test.com";
        String email3 = "rate-c-" + UUID.randomUUID() + "@test.com";

        mockMvc.perform(post("/api/v1/auth/register")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest(email1, "password123", "UserA"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest(email2, "password123", "UserB"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest(email3, "password123", "UserC"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.apex.com/errors/rate-limit-exceeded"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("rate-limit-exceeded"));
    }

    @Test
    void login_exceedsLimit_returns429() throws Exception {
        String email = "logintest-" + UUID.randomUUID() + "@test.com";
        registerUser(email);

        mockMvc.perform(post("/api/v1/auth/login")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("https://api.apex.com/errors/rate-limit-exceeded"));
    }

    @Test
    void trade_exceedsLimit_returns429() throws Exception {
        AuthResponse user = registerUser("trade1-" + UUID.randomUUID() + "@test.com");
        UUID assetId = btcAsset.getId();

        mockMvc.perform(post("/api/v1/trading/execute")
                .header("Authorization", "Bearer " + user.accessToken())
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ExecuteTradeRequest(assetId, OrderSide.BUY, new BigDecimal("0.001"), UUID.randomUUID().toString()))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/trading/execute")
                .header("Authorization", "Bearer " + user.accessToken())
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ExecuteTradeRequest(assetId, OrderSide.BUY, new BigDecimal("0.001"), UUID.randomUUID().toString()))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/trading/execute")
                .header("Authorization", "Bearer " + user.accessToken())
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ExecuteTradeRequest(assetId, OrderSide.BUY, new BigDecimal("0.001"), UUID.randomUUID().toString()))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("https://api.apex.com/errors/rate-limit-exceeded"));
    }

    @Test
    void trade_differentUsers_haveSeparateLimits() throws Exception {
        AuthResponse user1 = registerUser("tradediff1-" + UUID.randomUUID() + "@test.com");
        AuthResponse user2 = registerUser("tradediff2-" + UUID.randomUUID() + "@test.com");
        UUID assetId = btcAsset.getId();

        mockMvc.perform(post("/api/v1/trading/execute")
                .header("Authorization", "Bearer " + user1.accessToken())
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ExecuteTradeRequest(assetId, OrderSide.BUY, new BigDecimal("0.001"), UUID.randomUUID().toString()))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/trading/execute")
                .header("Authorization", "Bearer " + user1.accessToken())
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ExecuteTradeRequest(assetId, OrderSide.BUY, new BigDecimal("0.001"), UUID.randomUUID().toString()))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/trading/execute")
                .header("Authorization", "Bearer " + user1.accessToken())
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ExecuteTradeRequest(assetId, OrderSide.BUY, new BigDecimal("0.001"), UUID.randomUUID().toString()))))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(post("/api/v1/trading/execute")
                .header("Authorization", "Bearer " + user2.accessToken())
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ExecuteTradeRequest(assetId, OrderSide.BUY, new BigDecimal("0.001"), UUID.randomUUID().toString()))))
                .andExpect(status().isCreated());
    }

    @Test
    void journal_exceedsLimit_returns429() throws Exception {
        AuthResponse user = registerUser("journal1-" + UUID.randomUUID() + "@test.com");

        mockMvc.perform(post("/api/v1/journal/generate")
                .header("Authorization", "Bearer " + user.accessToken())
                .header("X-Forwarded-For", TEST_IP))
                .andReturn();

        mockMvc.perform(post("/api/v1/journal/generate")
                .header("Authorization", "Bearer " + user.accessToken())
                .header("X-Forwarded-For", TEST_IP))
                .andReturn();

        mockMvc.perform(post("/api/v1/journal/generate")
                .header("Authorization", "Bearer " + user.accessToken())
                .header("X-Forwarded-For", TEST_IP))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("https://api.apex.com/errors/rate-limit-exceeded"));
    }

    @Test
    void journal_differentUsers_haveSeparateLimits() throws Exception {
        AuthResponse user1 = registerUser("journaldiff1-" + UUID.randomUUID() + "@test.com");
        AuthResponse user2 = registerUser("journaldiff2-" + UUID.randomUUID() + "@test.com");

        mockMvc.perform(post("/api/v1/journal/generate")
                .header("Authorization", "Bearer " + user1.accessToken())
                .header("X-Forwarded-For", TEST_IP))
                .andReturn();

        mockMvc.perform(post("/api/v1/journal/generate")
                .header("Authorization", "Bearer " + user1.accessToken())
                .header("X-Forwarded-For", TEST_IP))
                .andReturn();

        mockMvc.perform(post("/api/v1/journal/generate")
                .header("Authorization", "Bearer " + user1.accessToken())
                .header("X-Forwarded-For", TEST_IP))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(post("/api/v1/journal/generate")
                .header("Authorization", "Bearer " + user2.accessToken())
                .header("X-Forwarded-For", TEST_IP))
                .andReturn();
    }

    @Test
    void register_afterWindowReset_succeeds() throws Exception {
        String email1 = "window1-" + UUID.randomUUID() + "@test.com";
        String email2 = "window2-" + UUID.randomUUID() + "@test.com";
        String email3 = "window3-" + UUID.randomUUID() + "@test.com";

        mockMvc.perform(post("/api/v1/auth/register")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest(email1, "password123", "User1"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest(email2, "password123", "User2"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest(email3, "password123", "User3"))))
                .andExpect(status().isTooManyRequests());

        redisTemplate.delete("rl:auth:register:" + TEST_IP);

        String email4 = "window4-" + UUID.randomUUID() + "@test.com";
        mockMvc.perform(post("/api/v1/auth/register")
                .header("X-Forwarded-For", TEST_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest(email4, "password123", "User4"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-RateLimit-Remaining", "1"));
    }
}
