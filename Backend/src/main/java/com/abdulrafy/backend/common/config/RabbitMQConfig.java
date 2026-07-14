package com.abdulrafy.backend.common.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String TRADE_EXCHANGE = "trading.exchange";
    public static final String TRADE_EXECUTED_QUEUE = "trading.trade-executed";
    public static final String ANALYTICS_QUEUE = "analytics.trade-executed";
    public static final String TRADE_EXECUTED_KEY = "trade.executed";

    @Bean
    public TopicExchange tradeExchange() {
        return ExchangeBuilder.topicExchange(TRADE_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue tradeExecutedQueue() {
        return QueueBuilder.durable(TRADE_EXECUTED_QUEUE).build();
    }

    @Bean
    public Queue analyticsQueue() {
        return QueueBuilder.durable(ANALYTICS_QUEUE).build();
    }

    @Bean
    public Binding tradeExecutedBinding(Queue tradeExecutedQueue, TopicExchange tradeExchange) {
        return BindingBuilder.bind(tradeExecutedQueue).to(tradeExchange).with(TRADE_EXECUTED_KEY);
    }

    @Bean
    public Binding analyticsBinding(Queue analyticsQueue, TopicExchange tradeExchange) {
        return BindingBuilder.bind(analyticsQueue).to(tradeExchange).with(TRADE_EXECUTED_KEY);
    }

    @Bean
    public JacksonJsonMessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         JacksonJsonMessageConverter jacksonJsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jacksonJsonMessageConverter);
        return rabbitTemplate;
    }
}
