package com.abdulrafy.backend.common.security;

import com.abdulrafy.backend.auth.service.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class WebSocketJwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketJwtHandshakeInterceptor.class);
    private static final String TOKEN_PARAM = "token";

    private final JwtService jwtService;

    public WebSocketJwtHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String query = request.getURI().getQuery();
        if (query == null) {
            log.debug("WebSocket handshake rejected: no query string");
            return false;
        }

        String token = extractToken(query);
        if (token == null) {
            log.debug("WebSocket handshake rejected: no token parameter");
            return false;
        }

        if (!jwtService.isTokenValid(token)) {
            log.debug("WebSocket handshake rejected: invalid token");
            return false;
        }

        attributes.put(TOKEN_PARAM, token);
        log.debug("WebSocket handshake accepted for user {}", jwtService.extractUserId(token));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private String extractToken(String query) {
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2 && TOKEN_PARAM.equals(pair[0])) {
                return pair[1];
            }
        }
        return null;
    }
}
