package com.abdulrafy.backend.trading.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
public class RedisPortfolioListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisPortfolioListener.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public RedisPortfolioListener(@Qualifier("apexBrokerMessagingTemplate") SimpMessagingTemplate brokerMessagingTemplate,
                                  ObjectMapper objectMapper) {
        this.messagingTemplate = brokerMessagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody());
            JsonNode root = objectMapper.readTree(json);
            String userId = root.get("userId").asText();
            JsonNode eventNode = root.get("event");
            messagingTemplate.convertAndSendToUser(userId, "/queue/portfolio",
                    objectMapper.convertValue(eventNode, TradeExecutedEvent.class));
        } catch (Exception e) {
            log.error("Failed to process Redis portfolio message", e);
        }
    }
}
