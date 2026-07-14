package com.abdulrafy.backend.trading.event;

import com.abdulrafy.backend.common.config.RedisPubSubConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

@Component
public class PortfolioBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(PortfolioBroadcaster.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PortfolioBroadcaster(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void broadcastPortfolioUpdate(UUID userId, TradeExecutedEvent event) {
        try {
            Map<String, Object> payload = Map.of(
                    "userId", userId.toString(),
                    "event", objectMapper.convertValue(event, Map.class));
            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.convertAndSend(RedisPubSubConfig.PORTFOLIO_CHANNEL, json);
        } catch (Exception e) {
            log.error("Failed to broadcast portfolio update for user {}", userId, e);
        }
    }
}
