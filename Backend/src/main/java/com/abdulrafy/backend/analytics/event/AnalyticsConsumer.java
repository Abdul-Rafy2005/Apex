package com.abdulrafy.backend.analytics.event;

import com.abdulrafy.backend.analytics.service.AnalyticsService;
import com.abdulrafy.backend.trading.event.TradeExecutedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsConsumer.class);

    private final AnalyticsService analyticsService;

    public AnalyticsConsumer(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Listens for TradeExecuted events and recomputes the analytics snapshot.
     * This is idempotent — reprocessing the same event just recomputes the same snapshot.
     */
    @RabbitListener(queues = "analytics.trade-executed")
    public void onTradeExecuted(TradeExecutedEvent event) {
        log.info("Analytics consumer received TradeExecuted: tradeId={}, portfolioId={}",
                event.tradeId(), event.portfolioId());

        try {
            analyticsService.recomputeSnapshot(event.portfolioId());
        } catch (Exception e) {
            log.error("Failed to recompute analytics for portfolio {}: {}",
                    event.portfolioId(), e.getMessage(), e);
        }
    }
}
