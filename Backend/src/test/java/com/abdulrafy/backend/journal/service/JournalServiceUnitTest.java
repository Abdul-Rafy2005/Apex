package com.abdulrafy.backend.journal.service;

import com.abdulrafy.backend.analytics.entity.PerformanceSnapshot;
import com.abdulrafy.backend.analytics.repository.PerformanceSnapshotRepository;
import com.abdulrafy.backend.auth.entity.User;
import com.abdulrafy.backend.auth.entity.Portfolio;
import com.abdulrafy.backend.auth.repository.PortfolioRepository;
import com.abdulrafy.backend.common.exception.ApexException;
import com.abdulrafy.backend.journal.entity.TradeJournalEntry;
import com.abdulrafy.backend.journal.repository.TradeJournalEntryRepository;
import com.abdulrafy.backend.trading.entity.Trade;
import com.abdulrafy.backend.trading.entity.OrderSide;
import com.abdulrafy.backend.trading.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JournalServiceUnitTest {

    @Mock
    private TradeJournalEntryRepository journalRepository;

    @Mock
    private PerformanceSnapshotRepository snapshotRepository;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    @InjectMocks
    private JournalService journalService;

    private UUID userId;
    private UUID portfolioId;
    private UUID snapshotId;
    private Portfolio portfolio;
    private PerformanceSnapshot snapshot;
    private FakeJournalGenerator fakeGenerator;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        portfolioId = UUID.randomUUID();
        snapshotId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .email("test@test.com")
                .passwordHash("hashed")
                .displayName("Test User")
                .build();

        portfolio = Portfolio.builder()
                .id(portfolioId)
                .user(user)
                .cashBalance(new BigDecimal("100000"))
                .build();

        snapshot = PerformanceSnapshot.builder()
                .id(snapshotId)
                .portfolioId(portfolioId)
                .snapshotDate(LocalDate.now(ZoneId.of("UTC")))
                .totalReturnPct(new BigDecimal("5.25"))
                .dailyReturnPct(new BigDecimal("2.10"))
                .totalTrades(10)
                .winningTrades(6)
                .losingTrades(4)
                .winRate(new BigDecimal("60.00"))
                .avgWinPct(new BigDecimal("8.50"))
                .avgLossPct(new BigDecimal("-3.20"))
                .largestGainPct(new BigDecimal("12.30"))
                .largestLossPct(new BigDecimal("-5.10"))
                .sharpeRatio(new BigDecimal("1.85"))
                .maxDrawdownPct(new BigDecimal("8.50"))
                .avgHoldingPeriodHours(new BigDecimal("4.50"))
                .riskScore(42)
                .portfolioValue(new BigDecimal("105250"))
                .cashBalance(new BigDecimal("50000"))
                .investedValue(new BigDecimal("55250"))
                .realizedPnl(new BigDecimal("1500"))
                .unrealizedPnl(new BigDecimal("320.50"))
                .bestAssetSymbol("BTC")
                .bestAssetReturnPct(new BigDecimal("12.30"))
                .worstAssetSymbol("ETH")
                .worstAssetReturnPct(new BigDecimal("-3.20"))
                .allocationBreakdown("{\"BTC\":6000.00,\"ETH\":4000.00}")
                .dailyPnlSeries("[]")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        fakeGenerator = new FakeJournalGenerator();
        journalService = new JournalService(
                journalRepository, snapshotRepository, tradeRepository, portfolioRepository,
                fakeGenerator, rabbitTemplate);
    }

    @Test
    void generateJournal_existingToday_throwsConflict() {
        TradeJournalEntry existing = TradeJournalEntry.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .entryDate(LocalDate.now(ZoneId.of("UTC")))
                .narrativeText("Already generated")
                .generatedAt(Instant.now())
                .build();

        when(journalRepository.findByUserIdAndEntryDate(userId, LocalDate.now(ZoneId.of("UTC"))))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> journalService.generateJournal(userId))
                .isInstanceOf(ApexException.class)
                .hasMessageContaining("already generated");
    }

    @Test
    void generateJournal_noSnapshot_throwsNotFound() {
        when(journalRepository.findByUserIdAndEntryDate(userId, LocalDate.now(ZoneId.of("UTC"))))
                .thenReturn(Optional.empty());
        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(portfolio));
        when(tradeRepository.findByPortfolioIdOrderByExecutedAtAsc(portfolioId))
                .thenReturn(java.util.List.of(
                        Trade.builder().id(UUID.randomUUID()).portfolioId(portfolioId)
                                .assetId(UUID.randomUUID())                                .side(OrderSide.BUY)
                                .quantity(new BigDecimal("1")).price(new BigDecimal("42000"))
                                .fee(new BigDecimal("1")).executedAt(Instant.now())
                                .idempotencyKey("idem-snap-test").build()));
        when(snapshotRepository.findByPortfolioIdAndSnapshotDate(portfolioId, LocalDate.now(ZoneId.of("UTC"))))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> journalService.generateJournal(userId))
                .isInstanceOf(ApexException.class)
                .hasMessageContaining("No analytics snapshot");
    }

    @Test
    void generateJournal_noTradesToday_throwsInsufficientTrades() {
        when(journalRepository.findByUserIdAndEntryDate(userId, LocalDate.now(ZoneId.of("UTC"))))
                .thenReturn(Optional.empty());
        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(portfolio));
        when(tradeRepository.findByPortfolioIdOrderByExecutedAtAsc(portfolioId))
                .thenReturn(java.util.List.of()); // No trades

        assertThatThrownBy(() -> journalService.generateJournal(userId))
                .isInstanceOf(ApexException.class)
                .hasMessageContaining("At least 1 trade");
    }

    @Test
    void generateJournal_validInput_createsEntry() {
        when(journalRepository.findByUserIdAndEntryDate(userId, LocalDate.now(ZoneId.of("UTC"))))
                .thenReturn(Optional.empty());
        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(portfolio));
        when(snapshotRepository.findByPortfolioIdAndSnapshotDate(portfolioId, LocalDate.now(ZoneId.of("UTC"))))
                .thenReturn(Optional.of(snapshot));

        // Create a trade that happened today
        Trade trade = Trade.builder()
                .id(UUID.randomUUID())
                .portfolioId(portfolioId)
                .executedAt(Instant.now())
                .build();
        when(tradeRepository.findByPortfolioIdOrderByExecutedAtAsc(portfolioId))
                .thenReturn(java.util.List.of(trade));
        when(journalRepository.save(any(TradeJournalEntry.class)))
                .thenAnswer(invocation -> {
                    TradeJournalEntry entry = invocation.getArgument(0);
                    entry.setId(UUID.randomUUID());
                    return entry;
                });

        TradeJournalEntry result = journalService.generateJournal(userId);

        assertThat(result).isNotNull();
        assertThat(result.getNarrativeText()).contains("Test narrative");
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getEntryDate()).isEqualTo(LocalDate.now(ZoneId.of("UTC")));

        // Verify the metrics were passed correctly
        assertThat(fakeGenerator.getLastMetrics()).isNotNull();
        assertThat(fakeGenerator.getLastMetrics().totalTrades()).isEqualTo(10);
        assertThat(fakeGenerator.getLastMetrics().winRate()).isEqualByComparingTo(new BigDecimal("60.00"));
    }
}
