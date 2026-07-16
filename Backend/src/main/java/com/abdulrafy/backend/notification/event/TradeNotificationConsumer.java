package com.abdulrafy.backend.notification.event;

import com.abdulrafy.backend.notification.service.NotificationService;
import com.abdulrafy.backend.trading.event.TradeExecutedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TradeNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(TradeNotificationConsumer.class);

    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    public TradeNotificationConsumer(
            NotificationService notificationService,
            @Qualifier("apexBrokerMessagingTemplate") SimpMessagingTemplate messagingTemplate) {
        this.notificationService = notificationService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Listens for TradeExecuted events and creates a "trade executed" notification.
     * Idempotent: duplicate delivery is suppressed by the service layer's
     * existsByUserIdAndTypeAndReferenceId check.
     */
    @RabbitListener(queues = "notifications.trade-executed")
    public void onTradeExecuted(TradeExecutedEvent event) {
        log.info("Trade notification consumer received: tradeId={}, portfolioId={}",
                event.tradeId(), event.portfolioId());

        try {
            UUID userId = notificationService.resolveUserIdByPortfolioId(event.portfolioId());
            if (userId == null) {
                log.warn("No user found for portfolio {}", event.portfolioId());
                return;
            }

            String title = "Trade Executed";
            String body = String.format("%s %s %.4f @ $%s",
                    event.side(), event.tradeId().toString().substring(0, 8),
                    event.quantity(), event.price());

            var notification = notificationService.createNotification(
                    userId, "TRADE_EXECUTED", title, body,
                    event.tradeId(), "TRADE");

            messagingTemplate.convertAndSendToUser(
                    userId.toString(), "/queue/notifications", notification);
        } catch (Exception e) {
            log.error("Failed to create trade notification: {}", e.getMessage(), e);
        }
    }
}
