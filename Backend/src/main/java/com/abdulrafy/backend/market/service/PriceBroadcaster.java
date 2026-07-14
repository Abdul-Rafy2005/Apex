package com.abdulrafy.backend.market.service;

import com.abdulrafy.backend.common.config.RedisPubSubConfig;
import com.abdulrafy.backend.market.dto.LivePriceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class PriceBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(PriceBroadcaster.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PriceBroadcaster(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void broadcastPrice(LivePriceResponse price) {
        try {
            String json = objectMapper.writeValueAsString(price);
            log.info("Broadcasting price to Redis channel {}: {}", RedisPubSubConfig.PRICE_CHANNEL, json);
            redisTemplate.convertAndSend(RedisPubSubConfig.PRICE_CHANNEL, json);
            log.info("Successfully sent price to Redis channel");
        } catch (Exception e) {
            log.error("Failed to broadcast price for {}", price.symbol(), e);
        }
    }
}
