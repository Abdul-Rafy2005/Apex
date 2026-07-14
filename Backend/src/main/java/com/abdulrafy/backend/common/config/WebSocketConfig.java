package com.abdulrafy.backend.common.config;

import com.abdulrafy.backend.common.security.WebSocketJwtChannelInterceptor;
import com.abdulrafy.backend.common.security.WebSocketJwtHandshakeInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketJwtHandshakeInterceptor handshakeInterceptor;
    private final WebSocketJwtChannelInterceptor channelInterceptor;

    public WebSocketConfig(WebSocketJwtHandshakeInterceptor handshakeInterceptor,
                           WebSocketJwtChannelInterceptor channelInterceptor) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.channelInterceptor = channelInterceptor;
    }

    @Override
    public void configureMessageBroker(org.springframework.messaging.simp.config.MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setTaskScheduler(brokerTaskScheduler());
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        converter.setObjectMapper(objectMapper);
        messageConverters.add(converter);
        return true;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(handshakeInterceptor)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(channelInterceptor);
    }

    @Bean
    public TaskScheduler brokerTaskScheduler() {
        return new ThreadPoolTaskScheduler();
    }

    @Bean
    public org.springframework.messaging.simp.SimpMessagingTemplate apexBrokerMessagingTemplate(
            @Qualifier("clientInboundChannel") MessageChannel clientInboundChannel,
            MappingJackson2MessageConverter stompMessageConverter) {
        org.springframework.messaging.simp.SimpMessagingTemplate template =
                new org.springframework.messaging.simp.SimpMessagingTemplate(clientInboundChannel);
        template.setMessageConverter(stompMessageConverter);
        return template;
    }

    @Bean
    public MappingJackson2MessageConverter stompMessageConverter() {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        converter.setObjectMapper(objectMapper);
        return converter;
    }
}
