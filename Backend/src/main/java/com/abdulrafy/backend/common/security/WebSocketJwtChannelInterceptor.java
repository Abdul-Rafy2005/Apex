package com.abdulrafy.backend.common.security;

import com.abdulrafy.backend.auth.service.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class WebSocketJwtChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketJwtChannelInterceptor.class);
    private static final String TOKEN_ATTR = "token";

    private final JwtService jwtService;

    public WebSocketJwtChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = (String) accessor.getSessionAttributes().get(TOKEN_ATTR);
            if (token != null && jwtService.isTokenValid(token)) {
                UUID userId = jwtService.extractUserId(token);
                String email = jwtService.extractEmail(token);
                String role = jwtService.extractRole(token);

                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                var authentication = new UsernamePasswordAuthenticationToken(
                    new AuthenticatedUser(userId, email, role), null, authorities);
                accessor.setUser(authentication);
                log.debug("WebSocket STOMP CONNECT authenticated for user {}", userId);
            } else {
                log.debug("WebSocket STOMP CONNECT rejected: invalid or missing token");
            }
        }

        return message;
    }
}
