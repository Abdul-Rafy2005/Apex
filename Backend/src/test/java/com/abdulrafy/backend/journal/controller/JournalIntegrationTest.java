package com.abdulrafy.backend.journal.controller;

import com.abdulrafy.backend.IntegrationTestBase;
import com.abdulrafy.backend.analytics.entity.PerformanceSnapshot;
import com.abdulrafy.backend.analytics.repository.PerformanceSnapshotRepository;
import com.abdulrafy.backend.auth.dto.AuthResponse;
import com.abdulrafy.backend.auth.dto.RegisterRequest;
import com.abdulrafy.backend.auth.entity.User;
import com.abdulrafy.backend.auth.repository.PortfolioRepository;
import com.abdulrafy.backend.auth.repository.UserRepository;
import com.abdulrafy.backend.journal.entity.TradeJournalEntry;
import com.abdulrafy.backend.journal.repository.TradeJournalEntryRepository;
import com.abdulrafy.backend.journal.service.AiJournalGenerator;
import com.abdulrafy.backend.market.entity.Asset;
import com.abdulrafy.backend.market.repository.AssetRepository;
import com.abdulrafy.backend.trading.entity.OrderSide;
import com.abdulrafy.backend.trading.entity.Trade;
import com.abdulrafy.backend.trading.repository.HoldingRepository;
import com.abdulrafy.backend.trading.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JournalIntegrationTest extends IntegrationTestBase {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private PerformanceSnapshotRepository snapshotRepository;
    @Autowired private TradeJournalEntryRepository journalRepository;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AiJournalGenerator journalGenerator;

    private MockMvc mockMvc;
    private AuthResponse authResponse;
    private UUID portfolioId;
    private Asset btcAsset;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        journalRepository.deleteAllInBatch();
        tradeRepository.deleteAllInBatch();
        snapshotRepository.deleteAllInBatch();
        holdingRepository.deleteAllInBatch();
        assetRepository.deleteAllInBatch();

        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });

        btcAsset = Asset.builder()
                .symbol("BTC").name("Bitcoin").precision(8)
                .providerSource("manual").tradable(true).build();
        assetRepository.saveAndFlush(btcAsset);

        String uniqueEmail = "journal-" + UUID.randomUUID() + "@test.com";
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest(uniqueEmail, "Password123!", "Journal User"))))
                .andExpect(status().isCreated())
                .andReturn();

        authResponse = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(), AuthResponse.class);

        User user = userRepository.findByEmail(uniqueEmail).orElseThrow();
        userId = user.getId();
        portfolioId = portfolioRepository.findByUserId(userId).orElseThrow().getId();

        when(journalGenerator.generateNarrative(any(AiJournalGenerator.JournalMetrics.class)))
                .thenReturn("Test narrative: Your trading session showed a 60% win rate with BTC as your best performer.");
    }

    private PerformanceSnapshot createSnapshot(LocalDate date) {
        return snapshotRepository.save(PerformanceSnapshot.builder()
                .portfolioId(portfolioId)
                .snapshotDate(date)
                .totalReturnPct(new BigDecimal("5.25"))
                .dailyReturnPct(new BigDecimal("2.10"))
                .totalTrades(1)
                .winningTrades(0)
                .losingTrades(0)
                .winRate(BigDecimal.ZERO)
                .avgWinPct(BigDecimal.ZERO)
                .avgLossPct(BigDecimal.ZERO)
                .largestGainPct(BigDecimal.ZERO)
                .largestLossPct(BigDecimal.ZERO)
                .sharpeRatio(BigDecimal.ZERO)
                .maxDrawdownPct(BigDecimal.ZERO)
                .avgHoldingPeriodHours(BigDecimal.ZERO)
                .riskScore(0)
                .portfolioValue(new BigDecimal("105250"))
                .cashBalance(new BigDecimal("50000"))
                .investedValue(new BigDecimal("55250"))
                .realizedPnl(BigDecimal.ZERO)
                .unrealizedPnl(new BigDecimal("320.50"))
                .bestAssetSymbol("BTC")
                .bestAssetReturnPct(new BigDecimal("12.30"))
                .worstAssetSymbol("BTC")
                .worstAssetReturnPct(new BigDecimal("12.30"))
                .allocationBreakdown("{\"BTC\":55250.00}")
                .dailyPnlSeries("[]")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    @Test
    void generateJournal_validTrade_createsEntry() throws Exception {
        tradeRepository.save(Trade.builder()
                .portfolioId(portfolioId)
                .assetId(btcAsset.getId())
                .side(OrderSide.BUY)
                .quantity(new BigDecimal("1"))
                .price(new BigDecimal("42000"))
                .fee(new BigDecimal("42"))
                .idempotencyKey("idem-journal-1")
                .executedAt(Instant.now())
                .build());

        createSnapshot(LocalDate.now(ZoneId.of("UTC")));

        mockMvc.perform(post("/api/v1/journal/generate")
                .header("Authorization", "Bearer " + authResponse.accessToken())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.narrativeText").value("Test narrative: Your trading session showed a 60% win rate with BTC as your best performer."));

        assertThat(journalRepository.findByUserIdAndEntryDate(userId, LocalDate.now(ZoneId.of("UTC")))).isPresent();
    }

    @Test
    void generateJournal_duplicateToday_throwsConflict() throws Exception {
        PerformanceSnapshot snapshot = createSnapshot(LocalDate.now(ZoneId.of("UTC")));

        journalRepository.save(TradeJournalEntry.builder()
                .userId(userId)
                .snapshotId(snapshot.getId())
                .entryDate(LocalDate.now(ZoneId.of("UTC")))
                .narrativeText("Already generated")
                .generatedAt(Instant.now())
                .build());

        mockMvc.perform(post("/api/v1/journal/generate")
                .header("Authorization", "Bearer " + authResponse.accessToken())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    void getJournalEntries_returnsPaginatedResults() throws Exception {
        PerformanceSnapshot snapshot = createSnapshot(LocalDate.now(ZoneId.of("UTC")));

        for (int i = 0; i < 5; i++) {
            journalRepository.save(TradeJournalEntry.builder()
                    .userId(userId)
                    .snapshotId(snapshot.getId())
                    .entryDate(LocalDate.now(ZoneId.of("UTC")).minusDays(i))
                    .narrativeText("Narrative for day " + i)
                    .generatedAt(Instant.now().minusSeconds(i * 86400L))
                    .build());
        }

        mockMvc.perform(get("/api/v1/journal")
                .header("Authorization", "Bearer " + authResponse.accessToken())
                .param("page", "0")
                .param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void getJournalEntries_crossTenant_userCannotSeeOtherEntries() throws Exception {
        PerformanceSnapshot snapshot = createSnapshot(LocalDate.now(ZoneId.of("UTC")));

        journalRepository.save(TradeJournalEntry.builder()
                .userId(userId)
                .snapshotId(snapshot.getId())
                .entryDate(LocalDate.now(ZoneId.of("UTC")))
                .narrativeText("My entry")
                .generatedAt(Instant.now())
                .build());

        // Register a second user to get a valid userId
        String otherEmail = "other-" + UUID.randomUUID() + "@test.com";
        MvcResult otherResult = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest(otherEmail, "Password123!", "Other User"))))
                .andExpect(status().isCreated())
                .andReturn();
        AuthResponse otherAuth = objectMapper.readValue(
                otherResult.getResponse().getContentAsString(), AuthResponse.class);
        UUID otherUserId = otherAuth.user().id();

        PerformanceSnapshot otherSnapshot = createSnapshot(LocalDate.now(ZoneId.of("UTC")).minusDays(100));

        journalRepository.save(TradeJournalEntry.builder()
                .userId(otherUserId)
                .snapshotId(otherSnapshot.getId())
                .entryDate(LocalDate.now(ZoneId.of("UTC")))
                .narrativeText("Other user entry")
                .generatedAt(Instant.now())
                .build());

        mockMvc.perform(get("/api/v1/journal")
                .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].narrativeText").value("My entry"));
    }

    @Test
    void generateJournal_unauthenticated_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/journal/generate")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
