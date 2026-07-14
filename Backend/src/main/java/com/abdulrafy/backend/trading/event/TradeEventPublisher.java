package com.abdulrafy.backend.trading.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class TradeEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TradeEventPublisher.class);
    public static final String TRADE_EXCHANGE = "trading.exchange";
    public static final String TRADE_EXECUTED_KEY = "trade.executed";

    private final RabbitTemplate rabbitTemplate;

    public TradeEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(TradeExecutedEvent event) {
        log.info("Publishing TradeExecuted event for trade {}", event.tradeId());
        rabbitTemplate.convertAndSend(TRADE_EXCHANGE, TRADE_EXECUTED_KEY, event);
    }
}
