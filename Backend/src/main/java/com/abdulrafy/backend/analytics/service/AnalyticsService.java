package com.abdulrafy.backend.analytics.service;

import com.abdulrafy.backend.analytics.dto.AllocationEntry;
import com.abdulrafy.backend.analytics.dto.AnalyticsHistoryResponse;
import com.abdulrafy.backend.analytics.dto.AnalyticsSummaryResponse;
import com.abdulrafy.backend.analytics.dto.DailyPnlEntry;
import com.abdulrafy.backend.analytics.entity.PerformanceSnapshot;
import com.abdulrafy.backend.analytics.repository.PerformanceSnapshotRepository;
import com.abdulrafy.backend.auth.entity.Portfolio;
import com.abdulrafy.backend.auth.repository.PortfolioRepository;
import com.abdulrafy.backend.market.entity.Asset;
import com.abdulrafy.backend.market.repository.AssetRepository;
import com.abdulrafy.backend.market.service.MarketService;
import com.abdulrafy.backend.trading.entity.Holding;
import com.abdulrafy.backend.trading.entity.OrderSide;
import com.abdulrafy.backend.trading.entity.Trade;
import com.abdulrafy.backend.trading.repository.HoldingRepository;
import com.abdulrafy.backend.trading.repository.TradeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    /**
     * Initial portfolio seed balance (from application.yml apex.auth.seed-balance).
     * Every metric that computes % return uses this as the denominator.
     */
    static final BigDecimal SEED_BALANCE = new BigDecimal("100000");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal SQRT_252 = new BigDecimal("15.8745");

    private static final String SUMMARY_CACHE_PREFIX = "analytics:summary:";

    private final PerformanceSnapshotRepository snapshotRepository;
    private final TradeRepository tradeRepository;
    private final HoldingRepository holdingRepository;
    private final PortfolioRepository portfolioRepository;
    private final AssetRepository assetRepository;
    private final MarketService marketService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public AnalyticsService(PerformanceSnapshotRepository snapshotRepository,
                            TradeRepository tradeRepository,
                            HoldingRepository holdingRepository,
                            PortfolioRepository portfolioRepository,
                            AssetRepository assetRepository,
                            MarketService marketService,
                            StringRedisTemplate redisTemplate) {
        this.snapshotRepository = snapshotRepository;
        this.tradeRepository = tradeRepository;
        this.holdingRepository = holdingRepository;
        this.portfolioRepository = portfolioRepository;
        this.assetRepository = assetRepository;
        this.marketService = marketService;
        this.redisTemplate = redisTemplate;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    // ─── Public API ──────────────────────────────────────────────────────

    /**
     * Recompute and persist the performance snapshot for a portfolio.
     * Called asynchronously via RabbitMQ consumer on every TradeExecuted event.
     */
    @Transactional
    public PerformanceSnapshot recomputeSnapshot(UUID portfolioId) {
        log.info("Recomputing analytics snapshot for portfolio {}", portfolioId);

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new NoSuchElementException("Portfolio not found: " + portfolioId));

        List<Trade> trades = tradeRepository.findByPortfolioIdOrderByExecutedAtAsc(portfolioId);
        List<Holding> holdings = holdingRepository.findByPortfolioId(portfolioId);
        Map<UUID, BigDecimal> currentPrices = fetchCurrentPrices(holdings);

        SnapshotMetrics m = computeMetrics(portfolioId, trades, holdings, currentPrices, portfolio);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        PerformanceSnapshot snapshot = findOrCreateSnapshot(portfolioId, today);
        applyMetrics(snapshot, m);

        PerformanceSnapshot saved;
        try {
            saved = snapshotRepository.save(snapshot);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Race condition: another thread created the snapshot between our find and save.
            log.warn("Race condition saving snapshot for portfolio {}, retrying", portfolioId);
            snapshot = snapshotRepository.findByPortfolioIdAndSnapshotDate(portfolioId, today)
                    .orElseThrow(() -> e);
            applyMetrics(snapshot, m);
            saved = snapshotRepository.save(snapshot);
        }

        invalidateSummaryCache(portfolioId);

        log.info("Snapshot saved for portfolio {} on {}: return={}%, sharpe={}, risk={}",
                portfolioId, today, saved.getTotalReturnPct(), saved.getSharpeRatio(), saved.getRiskScore());
        return saved;
    }

    private PerformanceSnapshot findOrCreateSnapshot(UUID portfolioId, LocalDate date) {
        return snapshotRepository.findByPortfolioIdAndSnapshotDate(portfolioId, date)
                .orElseGet(() -> PerformanceSnapshot.builder()
                        .portfolioId(portfolioId)
                        .snapshotDate(date)
                        .build());
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse getSummary(UUID portfolioId) {
        String cacheKey = SUMMARY_CACHE_PREFIX + portfolioId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, AnalyticsSummaryResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize cached summary for portfolio {}", portfolioId);
            }
        }

        PerformanceSnapshot snapshot = snapshotRepository
                .findTopByPortfolioIdOrderBySnapshotDateDesc(portfolioId)
                .orElse(null);

        if (snapshot == null) return emptySummary();

        AnalyticsSummaryResponse response = toSummaryResponse(snapshot);
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, json, java.time.Duration.ofMinutes(5));
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache summary for portfolio {}", portfolioId);
        }
        return response;
    }

    @Transactional(readOnly = true)
    public AnalyticsHistoryResponse getHistory(UUID portfolioId) {
        List<PerformanceSnapshot> snapshots = snapshotRepository
                .findByPortfolioIdOrderBySnapshotDateAsc(portfolioId);

        List<AnalyticsHistoryResponse.SnapshotPoint> points = snapshots.stream()
                .map(s -> new AnalyticsHistoryResponse.SnapshotPoint(
                        s.getSnapshotDate(),
                        s.getTotalReturnPct(),
                        s.getPortfolioValue(),
                        s.getDailyReturnPct().multiply(s.getPortfolioValue()).divide(HUNDRED, 4, RoundingMode.HALF_UP),
                        s.getSharpeRatio(),
                        s.getMaxDrawdownPct()
                ))
                .toList();

        return new AnalyticsHistoryResponse(points);
    }

    void invalidateSummaryCache(UUID portfolioId) {
        redisTemplate.delete(SUMMARY_CACHE_PREFIX + portfolioId);
    }

    // ─── Core Metric Computation ─────────────────────────────────────────

    private SnapshotMetrics computeMetrics(UUID portfolioId, List<Trade> trades,
                                           List<Holding> holdings, Map<UUID, BigDecimal> currentPrices,
                                           Portfolio portfolio) {
        SnapshotMetrics m = new SnapshotMetrics();

        m.cashBalance = portfolio.getCashBalance();

        // ── Portfolio value = cash + Σ(holding.quantity × current_price) ──
        BigDecimal holdingsValue = BigDecimal.ZERO;
        for (Holding h : holdings) {
            BigDecimal price = currentPrices.getOrDefault(h.getAssetId(), h.getAvgEntryPrice());
            holdingsValue = holdingsValue.add(h.getQuantity().multiply(price, MathContext.DECIMAL128));
        }
        m.portfolioValue = m.cashBalance.add(holdingsValue);

        // ── Invested value = Σ(holding.quantity × avg_entry_price) ──
        m.investedValue = holdings.stream()
                .map(h -> h.getQuantity().multiply(h.getAvgEntryPrice(), MathContext.DECIMAL128))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── Total return % = ((portfolio_value − seed) / seed) × 100 ──
        m.totalReturnPct = m.portfolioValue.subtract(SEED_BALANCE)
                .multiply(HUNDRED).divide(SEED_BALANCE, 4, RoundingMode.HALF_UP);

        // ── Trade metrics via FIFO matching ──
        computeTradeMetrics(m, trades);

        // ── Daily return % ──
        m.dailyReturnPct = computeDailyReturn(portfolioId, m.portfolioValue);

        // ── Average holding period (hours) ──
        m.avgHoldingPeriodHours = computeAvgHoldingPeriod(trades);

        // ── Max drawdown % ──
        m.maxDrawdownPct = computeMaxDrawdown(portfolioId, m.portfolioValue);

        // ── Sharpe ratio ──
        m.sharpeRatio = computeSharpeRatio(portfolioId, m.totalReturnPct);

        // ── Realized P/L ──
        m.realizedPnl = computeRealizedPnl(trades);

        // ── Unrealized P/L = Σ((current_price − avg_entry_price) × quantity) ──
        m.unrealizedPnl = holdings.stream()
                .map(h -> {
                    BigDecimal cp = currentPrices.getOrDefault(h.getAssetId(), h.getAvgEntryPrice());
                    return cp.subtract(h.getAvgEntryPrice()).multiply(h.getQuantity(), MathContext.DECIMAL128);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── Best/Worst asset ──
        computeBestWorstAsset(m, holdings, currentPrices);

        // ── Allocation breakdown JSON ──
        m.allocationBreakdownJson = computeAllocation(holdings, currentPrices, m.portfolioValue);

        // ── Daily P/L series JSON ──
        m.dailyPnlSeriesJson = computeDailyPnlSeries(portfolioId);

        // ── Risk score (0-100) ──
        m.riskScore = computeRiskScore(m, portfolioId, holdings, currentPrices);

        return m;
    }

    /**
     * Trade metrics via FIFO matching.
     *
     * win_rate = (profitable_sells / total_sells) × 100
     * avg_win  = mean(return_pct) for sells where sell_price > buy_price
     * avg_loss = mean(return_pct) for sells where sell_price ≤ buy_price
     * largest_gain = max(return_pct) across all sells
     * largest_loss = min(return_pct) across all sells
     */
    private void computeTradeMetrics(SnapshotMetrics m, List<Trade> trades) {
        m.totalTrades = trades.size();

        // Build FIFO buy queues per asset
        Map<UUID, LinkedList<Trade>> buyQueues = new HashMap<>();
        for (Trade t : trades) {
            if (t.getSide() == OrderSide.BUY) {
                buyQueues.computeIfAbsent(t.getAssetId(), k -> new LinkedList<>()).add(t);
            }
        }

        List<BigDecimal> sellReturns = new ArrayList<>();
        for (Trade sell : trades) {
            if (sell.getSide() != OrderSide.SELL) continue;
            LinkedList<Trade> buys = buyQueues.get(sell.getAssetId());
            if (buys == null || buys.isEmpty()) continue;

            Trade buy = buys.poll();
            // return_pct = ((sell_price − buy_price) / buy_price) × 100
            BigDecimal ret = sell.getPrice().subtract(buy.getPrice())
                    .multiply(HUNDRED).divide(buy.getPrice(), 4, RoundingMode.HALF_UP);
            sellReturns.add(ret);
        }

        List<BigDecimal> wins = sellReturns.stream().filter(p -> p.compareTo(BigDecimal.ZERO) > 0).toList();
        List<BigDecimal> losses = sellReturns.stream().filter(p -> p.compareTo(BigDecimal.ZERO) <= 0).toList();

        m.winningTrades = wins.size();
        m.losingTrades = losses.size();

        int totalSells = sellReturns.size();
        m.winRate = totalSells > 0
                ? new BigDecimal(wins.size()).multiply(HUNDRED).divide(new BigDecimal(totalSells), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        m.avgWinPct = wins.isEmpty() ? BigDecimal.ZERO
                : wins.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(new BigDecimal(wins.size()), 4, RoundingMode.HALF_UP);

        m.avgLossPct = losses.isEmpty() ? BigDecimal.ZERO
                : losses.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(new BigDecimal(losses.size()), 4, RoundingMode.HALF_UP);

        m.largestGainPct = wins.isEmpty() ? BigDecimal.ZERO
                : wins.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        m.largestLossPct = losses.isEmpty() ? BigDecimal.ZERO
                : losses.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    /**
     * daily_return_pct = ((current_value − previous_value) / previous_value) × 100
     * If no previous snapshot: daily_return = total_return (first day).
     */
    private BigDecimal computeDailyReturn(UUID portfolioId, BigDecimal currentValue) {
        if (portfolioId == null) return BigDecimal.ZERO;

        Optional<PerformanceSnapshot> prev = snapshotRepository
                .findTopByPortfolioIdOrderBySnapshotDateDesc(portfolioId);

        if (prev.isEmpty()) {
            return currentValue.subtract(SEED_BALANCE)
                    .multiply(HUNDRED).divide(SEED_BALANCE, 4, RoundingMode.HALF_UP);
        }

        BigDecimal prevValue = prev.get().getPortfolioValue();
        if (prevValue.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        return currentValue.subtract(prevValue)
                .multiply(HUNDRED).divide(prevValue, 4, RoundingMode.HALF_UP);
    }

    /**
     * avg_holding_period_hours = mean(sell_time − buy_time) for FIFO-matched round trips.
     */
    private BigDecimal computeAvgHoldingPeriod(List<Trade> trades) {
        Map<UUID, LinkedList<Trade>> buyQueues = new HashMap<>();
        List<Long> periods = new ArrayList<>();

        for (Trade t : trades) {
            if (t.getSide() == OrderSide.BUY) {
                buyQueues.computeIfAbsent(t.getAssetId(), k -> new LinkedList<>()).add(t);
            } else if (t.getSide() == OrderSide.SELL) {
                LinkedList<Trade> buys = buyQueues.get(t.getAssetId());
                if (buys != null && !buys.isEmpty()) {
                    long hours = ChronoUnit.HOURS.between(buys.poll().getExecutedAt(), t.getExecutedAt());
                    periods.add(Math.max(0, hours));
                }
            }
        }

        if (periods.isEmpty()) return BigDecimal.ZERO;
        long total = periods.stream().mapToLong(Long::longValue).sum();
        return new BigDecimal(total).divide(new BigDecimal(periods.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * max_drawdown_pct = max((peak − trough) / peak × 100) across all peak-trough pairs.
     * Uses historical snapshot values + current.
     */
    private BigDecimal computeMaxDrawdown(UUID portfolioId, BigDecimal currentValue) {
        List<BigDecimal> values = getPortfolioValueHistory(portfolioId, currentValue);
        if (values.size() < 2) return BigDecimal.ZERO;

        BigDecimal peak = values.getFirst();
        BigDecimal maxDd = BigDecimal.ZERO;

        for (BigDecimal v : values) {
            if (v.compareTo(peak) > 0) peak = v;
            if (peak.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dd = peak.subtract(v).multiply(HUNDRED).divide(peak, 4, RoundingMode.HALF_UP);
                if (dd.compareTo(maxDd) > 0) maxDd = dd;
            }
        }
        return maxDd;
    }

    /**
     * sharpe_ratio = (mean_daily_return / std_dev_daily_return) × √252
     * risk-free rate = 0 per PRD.
     * If < 2 data points: returns 0.
     */
    private BigDecimal computeSharpeRatio(UUID portfolioId, BigDecimal totalReturnPct) {
        List<PerformanceSnapshot> history = snapshotRepository
                .findByPortfolioIdOrderBySnapshotDateAsc(portfolioId);

        List<BigDecimal> dailyReturns = history.stream()
                .map(PerformanceSnapshot::getDailyReturnPct)
                .collect(Collectors.toList());
        dailyReturns.add(totalReturnPct);

        if (dailyReturns.size() < 2) return BigDecimal.ZERO;

        BigDecimal sum = dailyReturns.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mean = sum.divide(new BigDecimal(dailyReturns.size()), 10, RoundingMode.HALF_UP);

        BigDecimal variance = dailyReturns.stream()
                .map(r -> r.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(dailyReturns.size()), 10, RoundingMode.HALF_UP);

        BigDecimal stdDev = bigSqrt(variance);
        if (stdDev.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        return mean.multiply(SQRT_252).divide(stdDev, 4, RoundingMode.HALF_UP);
    }

    /**
     * realized_pnl = Σ((sell_price − buy_price) × matched_qty − fees) for FIFO-matched trades.
     */
    private BigDecimal computeRealizedPnl(List<Trade> trades) {
        Map<UUID, LinkedList<Trade>> buyQueues = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Trade t : trades) {
            if (t.getSide() == OrderSide.BUY) {
                buyQueues.computeIfAbsent(t.getAssetId(), k -> new LinkedList<>()).add(t);
            } else if (t.getSide() == OrderSide.SELL) {
                LinkedList<Trade> buys = buyQueues.get(t.getAssetId());
                if (buys != null && !buys.isEmpty()) {
                    Trade buy = buys.poll();
                    BigDecimal pnl = t.getPrice().subtract(buy.getPrice())
                            .multiply(t.getQuantity(), MathContext.DECIMAL128)
                            .subtract(buy.getFee()).subtract(t.getFee());
                    total = total.add(pnl);
                }
            }
        }
        return total;
    }

    /**
     * best/worst asset by unrealized return %.
     * return_per_asset = ((current_price − avg_entry_price) / avg_entry_price) × 100
     */
    private void computeBestWorstAsset(SnapshotMetrics m, List<Holding> holdings,
                                        Map<UUID, BigDecimal> currentPrices) {
        if (holdings.isEmpty()) {
            m.bestAssetSymbol = null;
            m.worstAssetSymbol = null;
            return;
        }

        String bestSym = null, worstSym = null;
        BigDecimal bestRet = new BigDecimal("-999999");
        BigDecimal worstRet = new BigDecimal("999999");

        for (Holding h : holdings) {
            Asset asset = assetRepository.findById(h.getAssetId()).orElse(null);
            if (asset == null) continue;

            BigDecimal cp = currentPrices.getOrDefault(h.getAssetId(), h.getAvgEntryPrice());
            BigDecimal ret = cp.subtract(h.getAvgEntryPrice())
                    .multiply(HUNDRED).divide(h.getAvgEntryPrice(), 4, RoundingMode.HALF_UP);

            if (ret.compareTo(bestRet) > 0) { bestRet = ret; bestSym = asset.getSymbol(); }
            if (ret.compareTo(worstRet) < 0) { worstRet = ret; worstSym = asset.getSymbol(); }
        }

        m.bestAssetSymbol = bestSym;
        m.bestAssetReturnPct = bestSym != null ? bestRet : BigDecimal.ZERO;
        m.worstAssetSymbol = worstSym;
        m.worstAssetReturnPct = worstSym != null ? worstRet : BigDecimal.ZERO;
    }

    /**
     * Allocation breakdown JSON: [{"symbol":"BTC","value":50000,"pct":50.0},...]
     */
    private String computeAllocation(List<Holding> holdings, Map<UUID, BigDecimal> currentPrices,
                                     BigDecimal portfolioValue) {
        List<AllocationEntry> entries = new ArrayList<>();
        for (Holding h : holdings) {
            Asset asset = assetRepository.findById(h.getAssetId()).orElse(null);
            if (asset == null) continue;
            BigDecimal price = currentPrices.getOrDefault(h.getAssetId(), h.getAvgEntryPrice());
            BigDecimal value = h.getQuantity().multiply(price, MathContext.DECIMAL128);
            BigDecimal pct = portfolioValue.compareTo(BigDecimal.ZERO) > 0
                    ? value.multiply(HUNDRED).divide(portfolioValue, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            entries.add(new AllocationEntry(asset.getSymbol(), value, pct));
        }
        try { return objectMapper.writeValueAsString(entries); }
        catch (JsonProcessingException e) { return "[]"; }
    }

    /**
     * Daily P/L series JSON from historical snapshots.
     */
    private String computeDailyPnlSeries(UUID portfolioId) {
        List<PerformanceSnapshot> history = snapshotRepository
                .findByPortfolioIdOrderBySnapshotDateAsc(portfolioId);

        List<DailyPnlEntry> entries = history.stream()
                .map(s -> new DailyPnlEntry(
                        s.getSnapshotDate(),
                        s.getDailyReturnPct().multiply(s.getPortfolioValue()).divide(HUNDRED, 4, RoundingMode.HALF_UP)))
                .toList();

        try { return objectMapper.writeValueAsString(entries); }
        catch (JsonProcessingException e) { return "[]"; }
    }

    /**
     * Composite risk score (0-100):
     * - Concentration (0-33): HHI = Σ(market_share_i²), risk = HHI × 33
     * - Volatility  (0-34): min(std_dev_daily_returns × 10, 34)
     * - Drawdown    (0-33): min(max_drawdown_pct, 33)
     */
    private int computeRiskScore(SnapshotMetrics m, UUID portfolioId,
                                  List<Holding> holdings, Map<UUID, BigDecimal> currentPrices) {
        // Concentration risk via HHI
        BigDecimal hhi = BigDecimal.ZERO;
        if (m.portfolioValue.compareTo(BigDecimal.ZERO) > 0) {
            for (Holding h : holdings) {
                BigDecimal price = currentPrices.getOrDefault(h.getAssetId(), h.getAvgEntryPrice());
                BigDecimal value = h.getQuantity().multiply(price, MathContext.DECIMAL128);
                BigDecimal share = value.divide(m.portfolioValue, 10, RoundingMode.HALF_UP);
                hhi = hhi.add(share.pow(2));
            }
        }
        BigDecimal concRisk = hhi.multiply(new BigDecimal("33")).min(new BigDecimal("33"));

        // Volatility risk: use std dev of daily returns
        List<PerformanceSnapshot> history = snapshotRepository
                .findByPortfolioIdOrderBySnapshotDateAsc(portfolioId);
        BigDecimal volRisk = BigDecimal.ZERO;
        if (history.size() >= 2) {
            List<BigDecimal> returns = history.stream()
                    .map(PerformanceSnapshot::getDailyReturnPct).toList();
            BigDecimal mean = returns.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(new BigDecimal(returns.size()), 10, RoundingMode.HALF_UP);
            BigDecimal variance = returns.stream()
                    .map(r -> r.subtract(mean).pow(2))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(new BigDecimal(returns.size()), 10, RoundingMode.HALF_UP);
            BigDecimal stdDev = bigSqrt(variance);
            volRisk = stdDev.multiply(new BigDecimal("10")).min(new BigDecimal("34"));
        }

        // Drawdown risk
        BigDecimal ddRisk = m.maxDrawdownPct.min(new BigDecimal("33"));

        int score = concRisk.add(volRisk).add(ddRisk).intValue();
        return Math.max(0, Math.min(100, score));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private List<BigDecimal> getPortfolioValueHistory(UUID portfolioId, BigDecimal currentValue) {
        List<BigDecimal> values = new ArrayList<>();
        if (portfolioId != null) {
            snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId)
                    .forEach(s -> values.add(s.getPortfolioValue()));
        }
        values.add(currentValue);
        return values;
    }

    private Map<UUID, BigDecimal> fetchCurrentPrices(List<Holding> holdings) {
        Map<UUID, BigDecimal> prices = new HashMap<>();
        if (holdings.isEmpty()) return prices;

        // Collect symbols for held assets
        Map<UUID, String> assetIdToSymbol = new HashMap<>();
        for (Holding h : holdings) {
            assetRepository.findById(h.getAssetId()).ifPresent(a -> assetIdToSymbol.put(h.getAssetId(), a.getSymbol()));
        }

        // Fetch prices for all symbols at once
        List<String> symbols = new ArrayList<>(assetIdToSymbol.values());
        if (!symbols.isEmpty()) {
            try {
                Map<String, BigDecimal> symbolPrices = marketService.fetchLivePrices(symbols).stream()
                        .collect(Collectors.toMap(
                                com.abdulrafy.backend.market.dto.LivePriceResponse::symbol,
                                com.abdulrafy.backend.market.dto.LivePriceResponse::priceUsd));

                for (Holding h : holdings) {
                    String symbol = assetIdToSymbol.get(h.getAssetId());
                    if (symbol != null && symbolPrices.containsKey(symbol)) {
                        prices.put(h.getAssetId(), symbolPrices.get(symbol));
                    } else {
                        prices.put(h.getAssetId(), h.getAvgEntryPrice());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch prices: {}", e.getMessage());
                for (Holding h : holdings) {
                    prices.put(h.getAssetId(), h.getAvgEntryPrice());
                }
            }
        }

        return prices;
    }

    private BigDecimal bigSqrt(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        BigDecimal x = value;
        BigDecimal half = new BigDecimal("0.5");
        for (int i = 0; i < 50; i++) {
            x = x.add(value.divide(x, 10, RoundingMode.HALF_UP)).multiply(half);
        }
        return x;
    }

    private void applyMetrics(PerformanceSnapshot s, SnapshotMetrics m) {
        s.setTotalReturnPct(m.totalReturnPct);
        s.setDailyReturnPct(m.dailyReturnPct);
        s.setTotalTrades(m.totalTrades);
        s.setWinningTrades(m.winningTrades);
        s.setLosingTrades(m.losingTrades);
        s.setWinRate(m.winRate);
        s.setAvgWinPct(m.avgWinPct);
        s.setAvgLossPct(m.avgLossPct);
        s.setLargestGainPct(m.largestGainPct);
        s.setLargestLossPct(m.largestLossPct);
        s.setSharpeRatio(m.sharpeRatio);
        s.setMaxDrawdownPct(m.maxDrawdownPct);
        s.setAvgHoldingPeriodHours(m.avgHoldingPeriodHours);
        s.setRiskScore(m.riskScore);
        s.setPortfolioValue(m.portfolioValue);
        s.setCashBalance(m.cashBalance);
        s.setInvestedValue(m.investedValue);
        s.setRealizedPnl(m.realizedPnl);
        s.setUnrealizedPnl(m.unrealizedPnl);
        s.setBestAssetSymbol(m.bestAssetSymbol);
        s.setBestAssetReturnPct(m.bestAssetReturnPct);
        s.setWorstAssetSymbol(m.worstAssetSymbol);
        s.setWorstAssetReturnPct(m.worstAssetReturnPct);
        s.setAllocationBreakdown(m.allocationBreakdownJson);
        s.setDailyPnlSeries(m.dailyPnlSeriesJson);
    }

    private AnalyticsSummaryResponse emptySummary() {
        return new AnalyticsSummaryResponse(
                LocalDate.now(ZoneOffset.UTC),
                BigDecimal.ZERO, BigDecimal.ZERO,
                0, 0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0,
                SEED_BALANCE, SEED_BALANCE, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                null, BigDecimal.ZERO, null, BigDecimal.ZERO,
                List.of(), List.of()
        );
    }

    private AnalyticsSummaryResponse toSummaryResponse(PerformanceSnapshot s) {
        List<AllocationEntry> allocation;
        try { allocation = List.of(objectMapper.readValue(s.getAllocationBreakdown(), AllocationEntry[].class)); }
        catch (Exception e) { allocation = List.of(); }

        List<DailyPnlEntry> dailyPnl;
        try { dailyPnl = List.of(objectMapper.readValue(s.getDailyPnlSeries(), DailyPnlEntry[].class)); }
        catch (Exception e) { dailyPnl = List.of(); }

        return new AnalyticsSummaryResponse(
                s.getSnapshotDate(),
                s.getTotalReturnPct(), s.getDailyReturnPct(),
                s.getTotalTrades(), s.getWinningTrades(), s.getLosingTrades(),
                s.getWinRate(), s.getAvgWinPct(), s.getAvgLossPct(),
                s.getLargestGainPct(), s.getLargestLossPct(),
                s.getSharpeRatio(), s.getMaxDrawdownPct(),
                s.getAvgHoldingPeriodHours(), s.getRiskScore(),
                s.getPortfolioValue(), s.getCashBalance(), s.getInvestedValue(),
                s.getRealizedPnl(), s.getUnrealizedPnl(),
                s.getBestAssetSymbol(), s.getBestAssetReturnPct(),
                s.getWorstAssetSymbol(), s.getWorstAssetReturnPct(),
                allocation, dailyPnl
        );
    }

    // ─── Internal metrics container ───────────────────────────────────────

    static class SnapshotMetrics {
        BigDecimal totalReturnPct = BigDecimal.ZERO;
        BigDecimal dailyReturnPct = BigDecimal.ZERO;
        int totalTrades = 0;
        int winningTrades = 0;
        int losingTrades = 0;
        BigDecimal winRate = BigDecimal.ZERO;
        BigDecimal avgWinPct = BigDecimal.ZERO;
        BigDecimal avgLossPct = BigDecimal.ZERO;
        BigDecimal largestGainPct = BigDecimal.ZERO;
        BigDecimal largestLossPct = BigDecimal.ZERO;
        BigDecimal sharpeRatio = BigDecimal.ZERO;
        BigDecimal maxDrawdownPct = BigDecimal.ZERO;
        BigDecimal avgHoldingPeriodHours = BigDecimal.ZERO;
        int riskScore = 0;
        BigDecimal portfolioValue = BigDecimal.ZERO;
        BigDecimal cashBalance = BigDecimal.ZERO;
        BigDecimal investedValue = BigDecimal.ZERO;
        BigDecimal realizedPnl = BigDecimal.ZERO;
        BigDecimal unrealizedPnl = BigDecimal.ZERO;
        String bestAssetSymbol = null;
        BigDecimal bestAssetReturnPct = BigDecimal.ZERO;
        String worstAssetSymbol = null;
        BigDecimal worstAssetReturnPct = BigDecimal.ZERO;
        String allocationBreakdownJson = "[]";
        String dailyPnlSeriesJson = "[]";
    }
}
