package com.abdulrafy.backend.analytics.service;

import com.abdulrafy.backend.analytics.dto.AllocationEntry;
import com.abdulrafy.backend.analytics.dto.DailyPnlEntry;
import com.abdulrafy.backend.analytics.entity.PerformanceSnapshot;
import com.abdulrafy.backend.analytics.repository.PerformanceSnapshotRepository;
import com.abdulrafy.backend.auth.entity.Portfolio;
import com.abdulrafy.backend.auth.entity.User;
import com.abdulrafy.backend.auth.repository.PortfolioRepository;
import com.abdulrafy.backend.market.dto.LivePriceResponse;
import com.abdulrafy.backend.market.entity.Asset;
import com.abdulrafy.backend.market.repository.AssetRepository;
import com.abdulrafy.backend.market.service.MarketService;
import com.abdulrafy.backend.trading.entity.Holding;
import com.abdulrafy.backend.trading.entity.OrderSide;
import com.abdulrafy.backend.trading.entity.Trade;
import com.abdulrafy.backend.trading.repository.HoldingRepository;
import com.abdulrafy.backend.trading.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceUnitTest {

    @Mock private PerformanceSnapshotRepository snapshotRepository;
    @Mock private TradeRepository tradeRepository;
    @Mock private HoldingRepository holdingRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private MarketService marketService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private AnalyticsService analyticsService;

    private static final UUID PORTFOLIO_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID BTC_ID = UUID.randomUUID();
    private static final UUID ETH_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(
                snapshotRepository, tradeRepository, holdingRepository,
                portfolioRepository, assetRepository, marketService, redisTemplate);
    }

    private Portfolio portfolio(BigDecimal cash) {
        User user = User.builder().id(USER_ID).email("t@t.com")
                .passwordHash("h").displayName("T").build();
        return Portfolio.builder().id(PORTFOLIO_ID).user(user).cashBalance(cash).version(0).build();
    }

    private Asset asset(UUID id, String sym) {
        return Asset.builder().id(id).symbol(sym).name(sym).providerSource("cg").tradable(true).build();
    }

    private Trade trade(UUID assetId, OrderSide side, String qty, String price, String fee, Instant t) {
        return Trade.builder().id(UUID.randomUUID()).portfolioId(PORTFOLIO_ID).assetId(assetId)
                .side(side).quantity(new BigDecimal(qty)).price(new BigDecimal(price))
                .fee(new BigDecimal(fee)).idempotencyKey(UUID.randomUUID().toString())
                .executedAt(t).build();
    }

    private void mockCommon() {
        lenient().when(snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(PORTFOLIO_ID)).thenReturn(List.of());
        lenient().when(snapshotRepository.findTopByPortfolioIdOrderBySnapshotDateDesc(PORTFOLIO_ID)).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(PerformanceSnapshot.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void mockAssets() {
        lenient().when(assetRepository.findById(BTC_ID)).thenReturn(Optional.of(asset(BTC_ID, "BTC")));
        lenient().when(assetRepository.findById(ETH_ID)).thenReturn(Optional.of(asset(ETH_ID, "ETH")));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST 1: Zero trades - all metrics at default
    // Portfolio cash = 100000 (seed), no trades, no holdings
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void zeroTrades_allMetricsAtDefault() {
        mockCommon();
        when(portfolioRepository.findById(PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio(new BigDecimal("100000"))));
        when(tradeRepository.findByPortfolioIdOrderByExecutedAtAsc(PORTFOLIO_ID)).thenReturn(List.of());
        when(holdingRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        PerformanceSnapshot s = analyticsService.recomputeSnapshot(PORTFOLIO_ID);

        assertThat(s.getPortfolioValue()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(s.getTotalReturnPct()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(s.getTotalTrades()).isZero();
        assertThat(s.getWinRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(s.getWinningTrades()).isZero();
        assertThat(s.getLosingTrades()).isZero();
        assertThat(s.getRealizedPnl()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(s.getUnrealizedPnl()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST 2: Single BUY - unrealized gain, no sells
    //
    // Portfolio cash AFTER buy = 100000 - 50000 - 50 = 49950
    // Holding: 1 BTC at avg_entry=50000. Current price: 60000
    // Portfolio value = 49950 + 60000 = 109950
    // Total return = (109950 - 100000) / 100000 * 100 = 9.95%
    // No sells so winRate = 0
    // Unrealized P/L = (60000 - 50000) * 1 = 10000
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void singleBuy_unrealizedGain() {
        mockCommon();
        mockAssets();
        when(portfolioRepository.findById(PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio(new BigDecimal("49950"))));

        Instant now = Instant.now();
        Trade buy = trade(BTC_ID, OrderSide.BUY, "1", "50000", "50", now);
        when(tradeRepository.findByPortfolioIdOrderByExecutedAtAsc(PORTFOLIO_ID)).thenReturn(List.of(buy));

        Holding h = Holding.builder().id(UUID.randomUUID()).portfolioId(PORTFOLIO_ID)
                .assetId(BTC_ID).quantity(new BigDecimal("1")).avgEntryPrice(new BigDecimal("50000")).build();
        when(holdingRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(h));
        when(marketService.fetchLivePrices(anyList()))
                .thenReturn(List.of(new LivePriceResponse("BTC", new BigDecimal("60000"), BigDecimal.ZERO, now)));

        PerformanceSnapshot s = analyticsService.recomputeSnapshot(PORTFOLIO_ID);

        assertThat(s.getPortfolioValue()).isEqualByComparingTo(new BigDecimal("109950.0000"));
        assertThat(s.getTotalReturnPct()).isEqualByComparingTo(new BigDecimal("9.9500"));
        assertThat(s.getWinRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(s.getWinningTrades()).isZero();
        assertThat(s.getUnrealizedPnl()).isEqualByComparingTo(new BigDecimal("10000.0000"));
        assertThat(s.getRealizedPnl()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST 3: Single round trip (BUY + SELL) - winning trade
    //
    // Buy 1 BTC at 40000, fee=40. Sell 1 BTC at 50000, fee=50.
    // Portfolio cash AFTER = 100000 - 40040 + 49950 = 109910
    // No holdings. Portfolio value = 109910
    // Total return = (109910 - 100000) / 100000 * 100 = 9.91%
    // Return per sell = (50000 - 40000) / 40000 * 100 = 25% (win)
    // Win rate = 100%, avgWin = 25%
    // Realized P/L = (50000 - 40000)*1 - 40 - 50 = 9910
    // Holding period = 4 hours
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void singleRoundTrip_winningTrade() {
        mockCommon();
        mockAssets();
        when(portfolioRepository.findById(PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio(new BigDecimal("109910"))));

        Instant t1 = Instant.parse("2026-07-14T10:00:00Z");
        Instant t2 = Instant.parse("2026-07-14T14:00:00Z");

        Trade buy = trade(BTC_ID, OrderSide.BUY, "1", "40000", "40", t1);
        Trade sell = trade(BTC_ID, OrderSide.SELL, "1", "50000", "50", t2);
        when(tradeRepository.findByPortfolioIdOrderByExecutedAtAsc(PORTFOLIO_ID)).thenReturn(List.of(buy, sell));
        when(holdingRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        PerformanceSnapshot s = analyticsService.recomputeSnapshot(PORTFOLIO_ID);

        assertThat(s.getPortfolioValue()).isEqualByComparingTo(new BigDecimal("109910.0000"));
        assertThat(s.getTotalReturnPct()).isEqualByComparingTo(new BigDecimal("9.9100"));
        assertThat(s.getTotalTrades()).isEqualTo(2);
        assertThat(s.getWinningTrades()).isEqualTo(1);
        assertThat(s.getLosingTrades()).isZero();
        assertThat(s.getWinRate()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(s.getAvgWinPct()).isEqualByComparingTo(new BigDecimal("25.0000"));
        assertThat(s.getLargestGainPct()).isEqualByComparingTo(new BigDecimal("25.0000"));
        assertThat(s.getLargestLossPct()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(s.getRealizedPnl()).isEqualByComparingTo(new BigDecimal("9910.0000"));
        assertThat(s.getAvgHoldingPeriodHours()).isEqualByComparingTo(new BigDecimal("4.00"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST 4: Two round trips - mixed win/loss
    //
    // Buy 1 BTC at 40000, fee=40. Buy 1 ETH at 3000, fee=3.
    // Sell 1 BTC at 50000, fee=50 -> return = +25% (win)
    // Sell 1 ETH at 2400, fee=2.4 -> return = -20% (loss)
    //
    // Portfolio cash AFTER = 100000 - 40040 - 3003 + 49950 + 2397.6 = 109304.6
    // No holdings. Portfolio value = 109304.6
    // Total return = (109304.6 - 100000) / 100000 * 100 = 9.3046
    // totalTrades = 4, winningTrades = 1, losingTrades = 1
    // winRate = 50.00
    // avgWinPct = 25.0000, avgLossPct = -20.0000
    // largestGainPct = 25.0000, largestLossPct = -20.0000
    // realizedPnl = (50000-40000)*1 - 40 - 50 + (2400-3000)*1 - 3 - 2.4 = 9910 - 605.4 = 9304.6
    // avgHoldingPeriodHours = (2 + 2) / 2 = 2.00
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void twoRoundTrips_mixedWinLoss() {
        mockCommon();
        mockAssets();
        when(portfolioRepository.findById(PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio(new BigDecimal("109304.6"))));

        Instant t1 = Instant.parse("2026-07-14T08:00:00Z");
        Instant t2 = Instant.parse("2026-07-14T09:00:00Z");
        Instant t3 = Instant.parse("2026-07-14T10:00:00Z");
        Instant t4 = Instant.parse("2026-07-14T11:00:00Z");

        List<Trade> trades = List.of(
                trade(BTC_ID, OrderSide.BUY,  "1", "40000", "40", t1),
                trade(ETH_ID, OrderSide.BUY,  "1", "3000",  "3",  t2),
                trade(BTC_ID, OrderSide.SELL, "1", "50000", "50", t3),
                trade(ETH_ID, OrderSide.SELL, "1", "2400",  "2.4", t4));
        when(tradeRepository.findByPortfolioIdOrderByExecutedAtAsc(PORTFOLIO_ID)).thenReturn(trades);
        when(holdingRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        PerformanceSnapshot s = analyticsService.recomputeSnapshot(PORTFOLIO_ID);

        assertThat(s.getPortfolioValue()).isEqualByComparingTo(new BigDecimal("109304.6000"));
        assertThat(s.getTotalReturnPct()).isEqualByComparingTo(new BigDecimal("9.3046"));
        assertThat(s.getTotalTrades()).isEqualTo(4);
        assertThat(s.getWinningTrades()).isEqualTo(1);
        assertThat(s.getLosingTrades()).isEqualTo(1);
        assertThat(s.getWinRate()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(s.getAvgWinPct()).isEqualByComparingTo(new BigDecimal("25.0000"));
        assertThat(s.getAvgLossPct()).isEqualByComparingTo(new BigDecimal("-20.0000"));
        assertThat(s.getLargestGainPct()).isEqualByComparingTo(new BigDecimal("25.0000"));
        assertThat(s.getLargestLossPct()).isEqualByComparingTo(new BigDecimal("-20.0000"));
        assertThat(s.getRealizedPnl()).isEqualByComparingTo(new BigDecimal("9304.6000"));
        assertThat(s.getAvgHoldingPeriodHours()).isEqualByComparingTo(new BigDecimal("2.00"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST 5: Sharpe ratio — hand-verified with population stdDev
    //
    // Code uses POPULATION variance (divides by N, not N-1): line 397.
    //
    // Setup: 3 historical snapshots with dailyReturnPct = [1, -0.5, 2]
    //        Cash = 100500, no holdings, no trades
    //        → totalReturnPct = (100500 - 100000) / 100000 × 100 = 0.5
    //        → dailyReturns for Sharpe = [1, -0.5, 2, 0.5]
    //
    // Mean = (1 + (-0.5) + 2 + 0.5) / 4 = 3/4 = 0.75
    //
    // Population variance = Σ(r - mean)² / N:
    //   (1-0.75)²   = 0.0625
    //   (-0.5-0.75)² = 1.5625
    //   (2-0.75)²   = 1.5625
    //   (0.5-0.75)²  = 0.0625
    //   Sum = 3.25, Variance = 3.25 / 4 = 0.8125
    //
    // Population stdDev = √0.8125 ≈ 0.9013878189
    //
    // Sharpe = (mean / stdDev) × √252
    //        = (0.75 / 0.9013878189) × 15.8745
    //        = 0.8320610687 × 15.8745
    //        = 13.2084 (scale 4, HALF_UP)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void sharpeRatio_multiDayReturns_handVerified() {
        mockCommon();
        when(portfolioRepository.findById(PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio(new BigDecimal("100500"))));
        when(tradeRepository.findByPortfolioIdOrderByExecutedAtAsc(PORTFOLIO_ID)).thenReturn(List.of());
        when(holdingRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        // 3 historical snapshots with dailyReturnPct = [1, -0.5, 2]
        PerformanceSnapshot s1 = PerformanceSnapshot.builder()
                .portfolioValue(new BigDecimal("100000")).dailyReturnPct(new BigDecimal("1"))
                .snapshotDate(LocalDate.of(2026, 7, 11)).build();
        PerformanceSnapshot s2 = PerformanceSnapshot.builder()
                .portfolioValue(new BigDecimal("100500")).dailyReturnPct(new BigDecimal("-0.5"))
                .snapshotDate(LocalDate.of(2026, 7, 12)).build();
        PerformanceSnapshot s3 = PerformanceSnapshot.builder()
                .portfolioValue(new BigDecimal("101000")).dailyReturnPct(new BigDecimal("2"))
                .snapshotDate(LocalDate.of(2026, 7, 13)).build();
        when(snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(PORTFOLIO_ID))
                .thenReturn(List.of(s1, s2, s3));

        PerformanceSnapshot s = analyticsService.recomputeSnapshot(PORTFOLIO_ID);

        // dailyReturns = [1, -0.5, 2, 0.5], mean=0.75, popVar=0.8125
        // stdDev=√0.8125≈0.90139, Sharpe=0.75/0.90139×15.8745=13.2084
        assertThat(s.getSharpeRatio()).isEqualByComparingTo(new BigDecimal("13.2084"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST 6: Max drawdown — hand-verified with clear peak-to-trough
    //
    // Setup: 2 historical snapshots with portfolioValues [100000, 120000]
    //        Current portfolio value = 90000
    //        → valueSeries = [100000, 120000, 90000]
    //
    // Peak tracking:
    //   v=100000: peak=100000, dd=0
    //   v=120000: peak=120000, dd=0
    //   v=90000:  peak=120000, dd=(120000-90000)/120000×100 = 25%
    //
    // maxDrawdownPct = 25.0000
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void maxDrawdown_peakToTrough25pct_handVerified() {
        mockCommon();
        mockAssets();
        // Cash=0, BTC qty=1 at 45000, ETH qty=1 at 45000 → portfolio=90000
        when(portfolioRepository.findById(PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio(BigDecimal.ZERO)));
        when(tradeRepository.findByPortfolioIdOrderByExecutedAtAsc(PORTFOLIO_ID)).thenReturn(List.of());

        Holding btcH = Holding.builder().id(UUID.randomUUID()).portfolioId(PORTFOLIO_ID)
                .assetId(BTC_ID).quantity(new BigDecimal("1")).avgEntryPrice(new BigDecimal("45000")).build();
        Holding ethH = Holding.builder().id(UUID.randomUUID()).portfolioId(PORTFOLIO_ID)
                .assetId(ETH_ID).quantity(new BigDecimal("1")).avgEntryPrice(new BigDecimal("45000")).build();
        when(holdingRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(btcH, ethH));
        when(marketService.fetchLivePrices(anyList()))
                .thenReturn(List.of(
                        new LivePriceResponse("BTC", new BigDecimal("45000"), BigDecimal.ZERO, Instant.now()),
                        new LivePriceResponse("ETH", new BigDecimal("45000"), BigDecimal.ZERO, Instant.now())));

        // Historical: values [100000, 120000]
        PerformanceSnapshot s1 = PerformanceSnapshot.builder()
                .portfolioValue(new BigDecimal("100000")).dailyReturnPct(BigDecimal.ZERO)
                .snapshotDate(LocalDate.of(2026, 7, 12)).build();
        PerformanceSnapshot s2 = PerformanceSnapshot.builder()
                .portfolioValue(new BigDecimal("120000")).dailyReturnPct(BigDecimal.ZERO)
                .snapshotDate(LocalDate.of(2026, 7, 13)).build();
        when(snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(PORTFOLIO_ID))
                .thenReturn(List.of(s1, s2));

        PerformanceSnapshot s = analyticsService.recomputeSnapshot(PORTFOLIO_ID);

        // valueSeries=[100000, 120000, 90000], maxDD=(120000-90000)/120000×100=25%
        assertThat(s.getMaxDrawdownPct()).isEqualByComparingTo(new BigDecimal("25.0000"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST 7: Composite risk score — hand-verified components
    //
    // Setup:
    //   Holdings: BTC value=50000, ETH value=50000 (50/50 split)
    //   Portfolio value = 100000 (cash=0)
    //   2 snapshots with dailyReturnPct = [2, -1], portfolioValues [100000, 120000]
    //   Current value = 100000
    //
    // Concentration (0-33):
    //   HHI = 0.5² + 0.5² = 0.5
    //   concRisk = 0.5 × 33 = 16.5
    //
    // Volatility (0-34):
    //   returns = [2, -1] (from snapshots, NOT including current)
    //   mean = 0.5
    //   variance = ((2-0.5)² + (-1-0.5)²) / 2 = (2.25 + 2.25) / 2 = 2.25
    //   stdDev = √2.25 = 1.5
    //   volRisk = 1.5 × 10 = 15
    //
    // Drawdown (0-33):
    //   valueSeries = [100000, 120000, 100000]
    //   maxDD = (120000-100000)/120000×100 = 16.6667%
    //   ddRisk = min(16.6667, 33) = 16.6667
    //
    // Total = 16.5 + 15 + 16.6667 = 48.1667
    // intValue() = 48 (truncation toward zero)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void riskScore_knownComponents_handVerified() {
        mockCommon();
        mockAssets();
        when(portfolioRepository.findById(PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio(BigDecimal.ZERO)));
        when(tradeRepository.findByPortfolioIdOrderByExecutedAtAsc(PORTFOLIO_ID)).thenReturn(List.of());

        // 50/50 split: each at 50000
        Holding btcH = Holding.builder().id(UUID.randomUUID()).portfolioId(PORTFOLIO_ID)
                .assetId(BTC_ID).quantity(new BigDecimal("1")).avgEntryPrice(new BigDecimal("50000")).build();
        Holding ethH = Holding.builder().id(UUID.randomUUID()).portfolioId(PORTFOLIO_ID)
                .assetId(ETH_ID).quantity(new BigDecimal("1")).avgEntryPrice(new BigDecimal("50000")).build();
        when(holdingRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(btcH, ethH));
        when(marketService.fetchLivePrices(anyList()))
                .thenReturn(List.of(
                        new LivePriceResponse("BTC", new BigDecimal("50000"), BigDecimal.ZERO, Instant.now()),
                        new LivePriceResponse("ETH", new BigDecimal("50000"), BigDecimal.ZERO, Instant.now())));

        // Snapshots for volatility and drawdown
        PerformanceSnapshot s1 = PerformanceSnapshot.builder()
                .portfolioValue(new BigDecimal("100000")).dailyReturnPct(new BigDecimal("2"))
                .snapshotDate(LocalDate.of(2026, 7, 12)).build();
        PerformanceSnapshot s2 = PerformanceSnapshot.builder()
                .portfolioValue(new BigDecimal("120000")).dailyReturnPct(new BigDecimal("-1"))
                .snapshotDate(LocalDate.of(2026, 7, 13)).build();
        when(snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(PORTFOLIO_ID))
                .thenReturn(List.of(s1, s2));

        PerformanceSnapshot s = analyticsService.recomputeSnapshot(PORTFOLIO_ID);

        // HHI=0.5→concRisk=16.5, volRisk=15, ddRisk=16.6667, total=48.1667→48
        assertThat(s.getRiskScore()).isEqualTo(48);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST 8: Allocation breakdown JSON serialization
    //
    // Setup: BTC qty=2 at 50000→value=100000, ETH qty=10 at 5000→value=50000
    //        Portfolio value = 150000 (cash=0)
    //
    // BTC: pct = 100000×100/150000 = 66.67 (scale 2, HALF_UP)
    // ETH: pct = 50000×100/150000 = 33.33 (scale 2, HALF_UP)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void allocationBreakdown_jsonSerialization() {
        mockCommon();
        mockAssets();
        when(portfolioRepository.findById(PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio(BigDecimal.ZERO)));
        when(tradeRepository.findByPortfolioIdOrderByExecutedAtAsc(PORTFOLIO_ID)).thenReturn(List.of());

        Holding btcH = Holding.builder().id(UUID.randomUUID()).portfolioId(PORTFOLIO_ID)
                .assetId(BTC_ID).quantity(new BigDecimal("2")).avgEntryPrice(new BigDecimal("50000")).build();
        Holding ethH = Holding.builder().id(UUID.randomUUID()).portfolioId(PORTFOLIO_ID)
                .assetId(ETH_ID).quantity(new BigDecimal("10")).avgEntryPrice(new BigDecimal("5000")).build();
        when(holdingRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(btcH, ethH));
        when(marketService.fetchLivePrices(anyList()))
                .thenReturn(List.of(
                        new LivePriceResponse("BTC", new BigDecimal("50000"), BigDecimal.ZERO, Instant.now()),
                        new LivePriceResponse("ETH", new BigDecimal("5000"), BigDecimal.ZERO, Instant.now())));

        PerformanceSnapshot s = analyticsService.recomputeSnapshot(PORTFOLIO_ID);

        // Verify JSON is not empty
        String json = s.getAllocationBreakdown();
        assertThat(json).isNotNull();
        assertThat(json).isNotEqualTo("[]");
        assertThat(json).contains("BTC");
        assertThat(json).contains("ETH");

        // Parse with JavaTimeModule registered
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        List<AllocationEntry> allocation;
        try {
            allocation = List.of(mapper.readValue(json, AllocationEntry[].class));
        } catch (Exception e) { throw new RuntimeException(e); }

        assertThat(allocation).hasSize(2);
        AllocationEntry btc = allocation.stream().filter(a -> a.symbol().equals("BTC")).findFirst().orElseThrow();
        AllocationEntry eth = allocation.stream().filter(a -> a.symbol().equals("ETH")).findFirst().orElseThrow();

        assertThat(btc.value()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(btc.pct()).isEqualByComparingTo(new BigDecimal("66.67"));
        assertThat(eth.value()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(eth.pct()).isEqualByComparingTo(new BigDecimal("33.33"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST 9: Daily P/L series JSON serialization
    //
    // Setup: 2 historical snapshots
    //   s1: dailyReturnPct=2, portfolioValue=100000 → pnl = 2×100000/100 = 2000
    //   s2: dailyReturnPct=-1, portfolioValue=120000 → pnl = -1×120000/100 = -1200
    //
    // Note: computeDailyPnlSeries uses ALL historical snapshots (not current).
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void dailyPnlSeries_jsonSerialization() {
        // Don't call mockCommon() — we need specific snapshot data
        when(snapshotRepository.save(any(PerformanceSnapshot.class))).thenAnswer(i -> i.getArgument(0));
        when(portfolioRepository.findById(PORTFOLIO_ID))
                .thenReturn(Optional.of(portfolio(new BigDecimal("100000"))));
        when(tradeRepository.findByPortfolioIdOrderByExecutedAtAsc(PORTFOLIO_ID)).thenReturn(List.of());
        when(holdingRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(snapshotRepository.findByPortfolioIdAndSnapshotDate(eq(PORTFOLIO_ID), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(snapshotRepository.findTopByPortfolioIdOrderBySnapshotDateDesc(PORTFOLIO_ID))
                .thenReturn(Optional.empty());

        PerformanceSnapshot s1 = PerformanceSnapshot.builder()
                .portfolioValue(new BigDecimal("100000")).dailyReturnPct(new BigDecimal("2"))
                .snapshotDate(LocalDate.of(2026, 7, 12)).build();
        PerformanceSnapshot s2 = PerformanceSnapshot.builder()
                .portfolioValue(new BigDecimal("120000")).dailyReturnPct(new BigDecimal("-1"))
                .snapshotDate(LocalDate.of(2026, 7, 13)).build();
        when(snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(PORTFOLIO_ID))
                .thenReturn(List.of(s1, s2));

        PerformanceSnapshot s = analyticsService.recomputeSnapshot(PORTFOLIO_ID);

        // Verify JSON is not empty and contains the dates
        String json = s.getDailyPnlSeries();
        assertThat(json).isNotNull();
        assertThat(json).isNotEqualTo("[]");
        assertThat(json).contains("2026");
        assertThat(json).contains("2000");
        assertThat(json).contains("-1200");

        // Parse with JavaTimeModule registered (WRITE_DATES_AS_TIMESTAMPS is true in the service)
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        List<DailyPnlEntry> dailyPnl;
        try {
            dailyPnl = List.of(mapper.readValue(json, DailyPnlEntry[].class));
        } catch (Exception e) { throw new RuntimeException(e); }

        assertThat(dailyPnl).hasSize(2);
        DailyPnlEntry e1 = dailyPnl.get(0);
        DailyPnlEntry e2 = dailyPnl.get(1);

        assertThat(e1.date()).isEqualTo(LocalDate.of(2026, 7, 12));
        assertThat(e1.pnl()).isEqualByComparingTo(new BigDecimal("2000.0000"));
        assertThat(e2.date()).isEqualTo(LocalDate.of(2026, 7, 13));
        assertThat(e2.pnl()).isEqualByComparingTo(new BigDecimal("-1200.0000"));
    }
}
