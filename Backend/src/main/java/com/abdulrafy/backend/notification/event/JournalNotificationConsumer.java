package com.abdulrafy.backend.notification.event;

import com.abdulrafy.backend.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class JournalNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(JournalNotificationConsumer.class);

    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    public JournalNotificationConsumer(
            NotificationService notificationService,
            @Qualifier("apexBrokerMessagingTemplate") SimpMessagingTemplate messagingTemplate) {
        this.notificationService = notificationService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Listens for JournalGenerated events and creates a "journal ready" notification.
     * Idempotent: duplicate delivery is suppressed by the service layer's
     * existsByUserIdAndTypeAndReferenceId check.
     */
    @RabbitListener(queues = "notifications.journal-generated")
    public void onJournalGenerated(JournalGeneratedEvent event) {
        log.info("Journal notification consumer received: userId={}, journalEntryId={}",
                event.userId(), event.journalEntryId());

        try {
            var notification = notificationService.createNotification(
                    event.userId(), "JOURNAL_READY", "Journal Ready",
                    "Your daily trade journal for " + event.entryDate() + " is ready to read.",
                    event.journalEntryId(), "JOURNAL_ENTRY");

            messagingTemplate.convertAndSendToUser(
                    event.userId().toString(), "/queue/notifications", notification);
        } catch (Exception e) {
            log.error("Failed to create journal notification: {}", e.getMessage(), e);
        }
    }
}
