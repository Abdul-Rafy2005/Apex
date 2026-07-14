package com.abdulrafy.backend.common.config;

import com.abdulrafy.backend.market.service.RedisPriceListener;
import com.abdulrafy.backend.trading.event.RedisPortfolioListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisPubSubConfig {

    public static final String PRICE_CHANNEL = "ws:prices";
    public static final String PORTFOLIO_CHANNEL = "ws:portfolio";

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisPriceListener priceListener,
            RedisPortfolioListener portfolioListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(priceListener, new ChannelTopic(PRICE_CHANNEL));
        container.addMessageListener(portfolioListener, new ChannelTopic(PORTFOLIO_CHANNEL));
        return container;
    }
}
