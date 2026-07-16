package com.abdulrafy.backend.journal.service;

import com.abdulrafy.backend.analytics.entity.PerformanceSnapshot;
import com.abdulrafy.backend.analytics.repository.PerformanceSnapshotRepository;
import com.abdulrafy.backend.auth.repository.PortfolioRepository;
import com.abdulrafy.backend.common.exception.ApexException;
import com.abdulrafy.backend.journal.entity.TradeJournalEntry;
import com.abdulrafy.backend.journal.repository.TradeJournalEntryRepository;
import com.abdulrafy.backend.notification.event.JournalGeneratedEvent;
import com.abdulrafy.backend.trading.entity.Trade;
import com.abdulrafy.backend.trading.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JournalService {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final long RATE_LIMIT_HOURS = 1;
    private static final int MIN_TRADES_FOR_JOURNAL = 1;

    private final TradeJournalEntryRepository journalRepository;
    private final PerformanceSnapshotRepository snapshotRepository;
    private final TradeRepository tradeRepository;
    private final PortfolioRepository portfolioRepository;
    private final AiJournalGenerator journalGenerator;
    private final RabbitTemplate rabbitTemplate;

    @Transactional(readOnly = true)
    public Page<TradeJournalEntry> getJournalEntries(UUID userId, Pageable pageable) {
        return journalRepository.findByUserIdOrderByEntryDateDesc(userId, pageable);
    }

    @Transactional
    public TradeJournalEntry generateJournal(UUID userId) {
        LocalDate today = LocalDate.now(UTC);

        // Check if already generated today
        TradeJournalEntry existing = journalRepository.findByUserIdAndEntryDate(userId, today).orElse(null);
        if (existing != null) {
            throw new ApexException("JOURNAL_ALREADY_GENERATED", "Journal already generated for today", 409);
        }

        // Rate limit: max once per hour
        Instant oneHourAgo = Instant.now().minus(RATE_LIMIT_HOURS, ChronoUnit.HOURS);
        long recentCount = journalRepository.countByUserIdAndGeneratedAtAfter(userId, oneHourAgo);
        if (recentCount > 0) {
            throw new ApexException("RATE_LIMIT_EXCEEDED", "Rate limit: journal can only be generated once per hour", 429);
        }

        // Get user's portfolio
        UUID portfolioId = portfolioRepository.findByUserId(userId)
                .orElseThrow(() -> new ApexException("PORTFOLIO_NOT_FOUND", "Portfolio not found", 404))
                .getId();

        // Get today's trades for the user's portfolio — validate count before snapshot
        List<Trade> todayTrades = tradeRepository.findByPortfolioIdOrderByExecutedAtAsc(portfolioId);
        List<Trade> tradesToday = todayTrades.stream()
                .filter(t -> t.getExecutedAt().atZone(UTC).toLocalDate().equals(today))
                .toList();

        if (tradesToday.size() < MIN_TRADES_FOR_JOURNAL) {
            throw new ApexException("INSUFFICIENT_TRADES", "At least " + MIN_TRADES_FOR_JOURNAL + " trade(s) required to generate a journal entry", 400);
        }

        // Get today's snapshot
        PerformanceSnapshot snapshot = snapshotRepository
                .findByPortfolioIdAndSnapshotDate(portfolioId, today)
                .orElseThrow(() -> new ApexException("NO_SNAPSHOT", "No analytics snapshot available for today", 404));

        // Build metrics payload
        AiJournalGenerator.JournalMetrics metrics = buildMetrics(snapshot);

        // Generate narrative
        String narrative = journalGenerator.generateNarrative(metrics);

        // Save entry
        TradeJournalEntry entry = TradeJournalEntry.builder()
                .userId(userId)
                .snapshotId(snapshot.getId())
                .entryDate(today)
                .narrativeText(narrative)
                .generatedAt(Instant.now())
                .build();

        TradeJournalEntry saved = journalRepository.save(entry);
        log.info("Journal entry generated for user {} on {}", userId, today);

        publishJournalEvent(userId, saved.getId(), today.toString());

        return saved;
    }

    @Scheduled(cron = "0 0 23 * * ?", zone = "UTC")
    @Transactional
    public void generateDailyJournals() {
        log.info("Starting daily journal generation for active users");
        LocalDate yesterday = LocalDate.now(UTC).minusDays(1);

        // Find all users who had trades yesterday
        List<PerformanceSnapshot> yesterdaySnapshots = snapshotRepository
                .findBySnapshotDate(yesterday);

        for (PerformanceSnapshot snapshot : yesterdaySnapshots) {
            UUID userId = portfolioRepository.findUserIdByPortfolioId(snapshot.getPortfolioId())
                    .orElse(null);
            if (userId == null) continue;

            try {
                // Skip if already generated
                if (journalRepository.findByUserIdAndEntryDate(userId, yesterday).isPresent()) {
                    continue;
                }

                AiJournalGenerator.JournalMetrics metrics = buildMetrics(snapshot);
                String narrative = journalGenerator.generateNarrative(metrics);

                TradeJournalEntry entry = TradeJournalEntry.builder()
                        .userId(userId)
                        .snapshotId(snapshot.getId())
                        .entryDate(yesterday)
                        .narrativeText(narrative)
                        .generatedAt(Instant.now())
                        .build();

                journalRepository.save(entry);
                log.info("Daily journal generated for user {} on {}", userId, yesterday);
                publishJournalEvent(userId, entry.getId(), yesterday.toString());
            } catch (Exception e) {
                log.error("Failed to generate daily journal for user {}: {}", userId, e.getMessage());
            }
        }
    }

    private AiJournalGenerator.JournalMetrics buildMetrics(PerformanceSnapshot snapshot) {
        // Parse allocation breakdown from JSON string
        Map<String, BigDecimal> allocation = Map.of();
        if (snapshot.getAllocationBreakdown() != null && !snapshot.getAllocationBreakdown().isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> raw = new tools.jackson.databind.ObjectMapper()
                        .readValue(snapshot.getAllocationBreakdown(), Map.class);
                allocation = raw.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> new BigDecimal(e.getValue().toString())
                        ));
            } catch (Exception e) {
                log.warn("Failed to parse allocation breakdown: {}", e.getMessage());
            }
        }

        return new AiJournalGenerator.JournalMetrics(
                snapshot.getTotalTrades(),
                snapshot.getWinningTrades(),
                snapshot.getLosingTrades(),
                snapshot.getWinRate(),
                snapshot.getTotalReturnPct(),
                snapshot.getSharpeRatio(),
                snapshot.getMaxDrawdownPct(),
                snapshot.getRiskScore(),
                snapshot.getAvgHoldingPeriodHours(),
                snapshot.getBestAssetSymbol(),
                snapshot.getBestAssetReturnPct(),
                snapshot.getWorstAssetSymbol(),
                snapshot.getWorstAssetReturnPct(),
                snapshot.getRealizedPnl(),
                snapshot.getUnrealizedPnl(),
                allocation
        );
    }

    private void publishJournalEvent(UUID userId, UUID journalEntryId, String entryDate) {
        try {
            rabbitTemplate.convertAndSend(
                    "journal.exchange", "journal.generated",
                    new JournalGeneratedEvent(userId, journalEntryId, entryDate));
        } catch (Exception e) {
            log.error("Failed to publish journal event for user {}: {}", userId, e.getMessage());
        }
    }
}
