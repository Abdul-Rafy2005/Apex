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

    public static final String JOURNAL_EXCHANGE = "journal.exchange";
    public static final String JOURNAL_GENERATED_KEY = "journal.generated";

    public static final String NOTIFICATIONS_EXCHANGE = "notifications.exchange";
    public static final String NOTIFICATIONS_TRADE_QUEUE = "notifications.trade-executed";
    public static final String NOTIFICATIONS_TRADE_DLQ = "notifications.trade-executed.dlq";
    public static final String NOTIFICATIONS_JOURNAL_QUEUE = "notifications.journal-generated";
    public static final String NOTIFICATIONS_JOURNAL_DLQ = "notifications.journal-generated.dlq";

    // ── Trade exchange ──

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

    // ── Journal exchange ──

    @Bean
    public TopicExchange journalExchange() {
        return ExchangeBuilder.topicExchange(JOURNAL_EXCHANGE).durable(true).build();
    }

    // ── Notifications exchange with DLQ ──

    @Bean
    public TopicExchange notificationsExchange() {
        return ExchangeBuilder.topicExchange(NOTIFICATIONS_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue notificationsTradeQueue() {
        return QueueBuilder.durable(NOTIFICATIONS_TRADE_QUEUE)
                .deadLetterExchange(NOTIFICATIONS_EXCHANGE)
                .deadLetterRoutingKey(NOTIFICATIONS_TRADE_DLQ)
                .build();
    }

    @Bean
    public Queue notificationsTradeDlq() {
        return QueueBuilder.durable(NOTIFICATIONS_TRADE_DLQ).build();
    }

    @Bean
    public Queue notificationsJournalQueue() {
        return QueueBuilder.durable(NOTIFICATIONS_JOURNAL_QUEUE)
                .deadLetterExchange(NOTIFICATIONS_EXCHANGE)
                .deadLetterRoutingKey(NOTIFICATIONS_JOURNAL_DLQ)
                .build();
    }

    @Bean
    public Queue notificationsJournalDlq() {
        return QueueBuilder.durable(NOTIFICATIONS_JOURNAL_DLQ).build();
    }

    @Bean
    public Binding notificationsTradeBinding(Queue notificationsTradeQueue,
                                              TopicExchange notificationsExchange) {
        return BindingBuilder.bind(notificationsTradeQueue).to(notificationsExchange)
                .with("notification.trade.*");
    }

    @Bean
    public Binding notificationsTradeDlqBinding(Queue notificationsTradeDlq,
                                                 TopicExchange notificationsExchange) {
        return BindingBuilder.bind(notificationsTradeDlq).to(notificationsExchange)
                .with(NOTIFICATIONS_TRADE_DLQ);
    }

    @Bean
    public Binding notificationsJournalBinding(Queue notificationsJournalQueue,
                                                TopicExchange notificationsExchange) {
        return BindingBuilder.bind(notificationsJournalQueue).to(notificationsExchange)
                .with("notification.journal.*");
    }

    @Bean
    public Binding notificationsJournalDlqBinding(Queue notificationsJournalDlq,
                                                   TopicExchange notificationsExchange) {
        return BindingBuilder.bind(notificationsJournalDlq).to(notificationsExchange)
                .with(NOTIFICATIONS_JOURNAL_DLQ);
    }

    // ── Bindings from trade exchange to notification queues ──

    @Bean
    public Binding tradeToNotificationBinding(Queue notificationsTradeQueue,
                                               TopicExchange tradeExchange) {
        return BindingBuilder.bind(notificationsTradeQueue).to(tradeExchange)
                .with(TRADE_EXECUTED_KEY);
    }

    // ── Bindings from journal exchange to notification queues ──

    @Bean
    public Binding journalToNotificationBinding(Queue notificationsJournalQueue,
                                                TopicExchange journalExchange) {
        return BindingBuilder.bind(notificationsJournalQueue).to(journalExchange)
                .with(JOURNAL_GENERATED_KEY);
    }

    // ── Common ──

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
