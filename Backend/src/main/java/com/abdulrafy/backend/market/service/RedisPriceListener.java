package com.abdulrafy.backend.market.service;

import com.abdulrafy.backend.market.dto.LivePriceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class RedisPriceListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisPriceListener.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public RedisPriceListener(@Qualifier("apexBrokerMessagingTemplate") SimpMessagingTemplate brokerMessagingTemplate,
                              ObjectMapper objectMapper) {
        this.messagingTemplate = brokerMessagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody());
            log.info("Received Redis price message: {}", json);
            LivePriceResponse price = objectMapper.readValue(json, LivePriceResponse.class);
            log.info("Parsed price for {}, sending to /topic/prices/{}", price.symbol(), price.symbol());
            messagingTemplate.convertAndSend("/topic/prices/" + price.symbol(), price);
            log.info("Sent price to /topic/prices/{}", price.symbol());
        } catch (Exception e) {
            log.error("Failed to process Redis price message", e);
        }
    }
}
