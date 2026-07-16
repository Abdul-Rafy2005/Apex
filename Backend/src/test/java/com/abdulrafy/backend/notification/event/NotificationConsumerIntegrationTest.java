package com.abdulrafy.backend.notification.event;

import com.abdulrafy.backend.IntegrationTestBase;
import com.abdulrafy.backend.analytics.entity.PerformanceSnapshot;
import com.abdulrafy.backend.analytics.repository.PerformanceSnapshotRepository;
import com.abdulrafy.backend.auth.entity.Portfolio;
import com.abdulrafy.backend.auth.entity.User;
import com.abdulrafy.backend.auth.entity.UserRole;
import com.abdulrafy.backend.auth.repository.PortfolioRepository;
import com.abdulrafy.backend.auth.repository.UserRepository;
import com.abdulrafy.backend.market.entity.Asset;
import com.abdulrafy.backend.market.repository.AssetRepository;
import com.abdulrafy.backend.notification.repository.NotificationRepository;
import com.abdulrafy.backend.notification.service.NotificationService;
import com.abdulrafy.backend.trading.entity.OrderSide;
import com.abdulrafy.backend.trading.entity.Trade;
import com.abdulrafy.backend.trading.event.TradeExecutedEvent;
import com.abdulrafy.backend.trading.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NotificationConsumerIntegrationTest extends IntegrationTestBase {

    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private UserRepository userRepository;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private PerformanceSnapshotRepository snapshotRepository;

    private UUID userId;
    private UUID portfolioId;
    private UUID tradeId;
    private Asset btcAsset;

    /**
     * Polls the database until at least one unread notification exists for the user.
     * Times out after 10 seconds and fails the test if no notification appears.
     */
    private void waitForNotification(UUID uid) {
        for (int i = 0; i < 50; i++) {
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (notificationRepository.countByUserIdAndReadAtIsNull(uid) > 0) {
                return;
            }
        }
        org.junit.jupiter.api.Assertions.fail(
                "Timed out waiting for notification for user " + uid);
    }

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAllInBatch();
        tradeRepository.deleteAllInBatch();
        snapshotRepository.deleteAllInBatch();

        // Create user with portfolio
        User user = userRepository.save(User.builder()
                .email("consumer-" + UUID.randomUUID() + "@test.com")
                .passwordHash("hash")
                .displayName("Consumer Test")
                .role(UserRole.TRADER)
                .build());
        userId = user.getId();

        Portfolio portfolio = portfolioRepository.save(Portfolio.builder()
                .user(user)
                .cashBalance(new BigDecimal("100000"))
                .build());
        portfolioId = portfolio.getId();

        // Find or create asset
        btcAsset = assetRepository.findBySymbol("BTC").orElseGet(() ->
                assetRepository.save(Asset.builder()
                        .symbol("BTC").name("Bitcoin").precision(8)
                        .providerSource("manual").tradable(true).build()));

        // Create a trade
        Trade trade = tradeRepository.save(Trade.builder()
                .portfolioId(portfolioId)
                .assetId(btcAsset.getId())
                .side(OrderSide.BUY)
                .quantity(new BigDecimal("1"))
                .price(new BigDecimal("42000"))
                .fee(new BigDecimal("42"))
                .idempotencyKey("idem-consumer-" + UUID.randomUUID())
                .executedAt(Instant.now())
                .build());
        tradeId = trade.getId();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test: TradeNotificationConsumer creates notification on TradeExecuted
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void tradeNotificationConsumer_createsNotificationOnTradeExecuted() {
        TradeExecutedEvent event = new TradeExecutedEvent(
                tradeId, portfolioId, btcAsset.getId(), "BUY",
                new BigDecimal("1"), new BigDecimal("42000"),
                new BigDecimal("42"), Instant.now());

        rabbitTemplate.convertAndSend("trading.exchange", "trade.executed", event);

        waitForNotification(userId);
        assertThat(notificationRepository.countByUserIdAndReadAtIsNull(userId)).isEqualTo(1);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test: Duplicate trade event does NOT create duplicate notification
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void tradeNotificationConsumer_duplicateEvent_doesNotCreateDuplicate() {
        TradeExecutedEvent event = new TradeExecutedEvent(
                tradeId, portfolioId, btcAsset.getId(), "BUY",
                new BigDecimal("1"), new BigDecimal("42000"),
                new BigDecimal("42"), Instant.now());

        // Publish the same event twice — simulates true message redelivery
        rabbitTemplate.convertAndSend("trading.exchange", "trade.executed", event);
        rabbitTemplate.convertAndSend("trading.exchange", "trade.executed", event);

        // Wait for the first notification to appear (consumer is async)
        waitForNotification(userId);

        // Stabilization: give the second consumer invocation time to complete
        // before asserting count. Without this wait, the assertion could pass
        // spuriously if the second message hasn't been processed yet.
        try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Should still be exactly 1 — consumer-level idempotency prevented a duplicate
        assertThat(notificationRepository.countByUserIdAndReadAtIsNull(userId)).isEqualTo(1);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test: JournalNotificationConsumer creates notification on JournalGenerated
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void journalNotificationConsumer_createsNotificationOnJournalGenerated() {
        JournalGeneratedEvent event = new JournalGeneratedEvent(
                userId, UUID.randomUUID(), LocalDate.now(ZoneOffset.UTC).toString());

        rabbitTemplate.convertAndSend("journal.exchange", "journal.generated", event);

        waitForNotification(userId);
        var notifs = notificationRepository.findByUserIdOrderByReadAtAscCreatedAtDesc(
                userId, org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(notifs.getContent().get(0).getType()).isEqualTo("JOURNAL_READY");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test: Duplicate journal event does NOT create duplicate notification
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void journalNotificationConsumer_duplicateEvent_doesNotCreateDuplicate() {
        UUID journalId = UUID.randomUUID();
        JournalGeneratedEvent event = new JournalGeneratedEvent(
                userId, journalId, LocalDate.now(ZoneOffset.UTC).toString());

        // Publish the same event twice — simulates true message redelivery
        rabbitTemplate.convertAndSend("journal.exchange", "journal.generated", event);
        rabbitTemplate.convertAndSend("journal.exchange", "journal.generated", event);

        // Wait for the first notification to appear (consumer is async)
        waitForNotification(userId);

        // Stabilization: give the second consumer invocation time to complete
        try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Should still be exactly 1 — consumer-level idempotency prevented a duplicate
        assertThat(notificationRepository.countByUserIdAndReadAtIsNull(userId)).isEqualTo(1);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test: Trade notification is only for the owning user
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void tradeNotificationConsumer_onlyDeliversToOwningUser() {
        // Create second user
        User userB = userRepository.save(User.builder()
                .email("other-" + UUID.randomUUID() + "@test.com")
                .passwordHash("hash")
                .displayName("Other User")
                .role(UserRole.TRADER)
                .build());

        TradeExecutedEvent event = new TradeExecutedEvent(
                tradeId, portfolioId, btcAsset.getId(), "BUY",
                new BigDecimal("1"), new BigDecimal("42000"),
                new BigDecimal("42"), Instant.now());

        rabbitTemplate.convertAndSend("trading.exchange", "trade.executed", event);

        waitForNotification(userId);
        // User B should have no notifications
        assertThat(notificationRepository.countByUserIdAndReadAtIsNull(userB.getId())).isZero();
    }
}
