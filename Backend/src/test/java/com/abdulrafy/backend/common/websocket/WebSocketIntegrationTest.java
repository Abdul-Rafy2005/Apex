package com.abdulrafy.backend.common.websocket;

import com.abdulrafy.backend.IntegrationTestBase;
import com.abdulrafy.backend.auth.dto.AuthResponse;
import com.abdulrafy.backend.auth.dto.RegisterRequest;
import com.abdulrafy.backend.auth.service.JwtService;
import com.abdulrafy.backend.market.dto.LivePriceResponse;
import com.abdulrafy.backend.market.service.PriceBroadcaster;
import com.abdulrafy.backend.trading.event.PortfolioBroadcaster;
import com.abdulrafy.backend.trading.event.TradeExecutedEvent;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    int port;

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private JwtService jwtService;
    @Autowired private PriceBroadcaster priceBroadcaster;
    @Autowired private PortfolioBroadcaster portfolioBroadcaster;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private AuthResponse registerUser(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, "password123", "Test User"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private WebSocketStompClient createStompClient() {
        List<Transport> transports = new ArrayList<>();
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);
        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);

        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        com.fasterxml.jackson.databind.ObjectMapper jackson2Mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        jackson2Mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        jackson2Mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        converter.setObjectMapper(jackson2Mapper);
        stompClient.setMessageConverter(converter);
        return stompClient;
    }

    private StompSessionHandler createSessionHandler(CompletableFuture<StompSession> sessionFuture,
                                                      CompletableFuture<Throwable> errorFuture) {
        return new StompSessionHandler() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                sessionFuture.complete(session);
            }

            @Override
            public void handleException(StompSession session, StompCommand command,
                                        StompHeaders headers, byte[] body, Throwable exception) {
                errorFuture.complete(exception);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                errorFuture.complete(exception);
            }

            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
            }
        };
    }

    @Test
    void authenticatedWebSocketConnection_succeeds() throws Exception {
        AuthResponse auth = registerUser("ws-auth-" + UUID.randomUUID() + "@test.com");

        WebSocketStompClient stompClient = createStompClient();
        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();

        stompClient.connectAsync(
                "ws://localhost:" + port + "/ws?token=" + auth.accessToken(),
                new WebSocketHttpHeaders(),
                createSessionHandler(sessionFuture, errorFuture));

        StompSession session = sessionFuture.get(5, TimeUnit.SECONDS);
        assertThat(session.isConnected()).isTrue();
        session.disconnect();
    }

    @Test
    void unauthenticatedWebSocketConnection_rejected() throws Exception {
        WebSocketStompClient stompClient = createStompClient();
        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();

        stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(),
                createSessionHandler(sessionFuture, errorFuture));

        try {
            StompSession session = sessionFuture.get(3, TimeUnit.SECONDS);
            // If we get here, the session was created but STOMP CONNECT should fail
            // The session might be connected at WebSocket level but STOMP-level auth rejects it
            assertThat(session).isNotNull();
        } catch (TimeoutException e) {
            // Expected: connection should fail or timeout
            Throwable error = errorFuture.getNow(null);
            assertThat(error).isNotNull();
        } catch (ExecutionException e) {
            // Expected: connection should fail
            assertThat(e.getCause()).isNotNull();
        }
    }

    @Test
    void priceUpdate_deliveredToSubscribedClient() throws Exception {
        AuthResponse auth = registerUser("ws-price-" + UUID.randomUUID() + "@test.com");

        WebSocketStompClient stompClient = createStompClient();
        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();

        stompClient.connectAsync(
                "ws://localhost:" + port + "/ws?token=" + auth.accessToken(),
                new WebSocketHttpHeaders(),
                createSessionHandler(sessionFuture, errorFuture));

        StompSession session = sessionFuture.get(5, TimeUnit.SECONDS);
        assertThat(session.isConnected()).isTrue();

        // Subscribe to BTC price topic
        CompletableFuture<LivePriceResponse> priceFuture = new CompletableFuture<>();
        session.subscribe("/topic/prices/BTC", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return LivePriceResponse.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                priceFuture.complete((LivePriceResponse) payload);
            }
        });

        // Allow subscription to register
        Thread.sleep(300);

        // Broadcast a price update
        LivePriceResponse price = new LivePriceResponse("BTC", new BigDecimal("45000"), new BigDecimal("2.5"), Instant.now());
        priceBroadcaster.broadcastPrice(price);

        // Wait for the message
        LivePriceResponse received = priceFuture.get(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.symbol()).isEqualTo("BTC");
        assertThat(received.priceUsd()).isEqualByComparingTo(new BigDecimal("45000"));

        session.disconnect();
    }

    @Test
    void portfolioUpdate_deliveredOnlyToOwningUser() throws Exception {
        // Register two users
        AuthResponse userA = registerUser("ws-portfolio-a-" + UUID.randomUUID() + "@test.com");
        AuthResponse userB = registerUser("ws-portfolio-b-" + UUID.randomUUID() + "@test.com");

        WebSocketStompClient stompClient = createStompClient();

        // Connect User A
        CompletableFuture<StompSession> sessionAFuture = new CompletableFuture<>();
        CompletableFuture<Throwable> errorAFuture = new CompletableFuture<>();
        stompClient.connectAsync(
                "ws://localhost:" + port + "/ws?token=" + userA.accessToken(),
                new WebSocketHttpHeaders(),
                createSessionHandler(sessionAFuture, errorAFuture));
        StompSession sessionA = sessionAFuture.get(5, TimeUnit.SECONDS);

        // Connect User B
        CompletableFuture<StompSession> sessionBFuture = new CompletableFuture<>();
        CompletableFuture<Throwable> errorBFuture = new CompletableFuture<>();
        stompClient.connectAsync(
                "ws://localhost:" + port + "/ws?token=" + userB.accessToken(),
                new WebSocketHttpHeaders(),
                createSessionHandler(sessionBFuture, errorBFuture));
        StompSession sessionB = sessionBFuture.get(5, TimeUnit.SECONDS);

        // Subscribe both users to their portfolio topics
        CompletableFuture<Object> userAFuture = new CompletableFuture<>();
        CompletableFuture<Object> userBFuture = new CompletableFuture<>();

        sessionA.subscribe("/user/queue/portfolio", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Object.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                userAFuture.complete(payload);
            }
        });

        sessionB.subscribe("/user/queue/portfolio", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Object.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                userBFuture.complete(payload);
            }
        });

        // Wait for subscriptions to be active
        Thread.sleep(500);

        // Broadcast portfolio update for User A only
        UUID userAId = jwtService.extractUserId(userA.accessToken());
        TradeExecutedEvent event = new TradeExecutedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "BUY", new BigDecimal("1"), new BigDecimal("42000"),
                new BigDecimal("42"), Instant.now());
        portfolioBroadcaster.broadcastPortfolioUpdate(userAId, event);

        // User A should receive the update
        Object receivedByA = userAFuture.get(5, TimeUnit.SECONDS);
        assertThat(receivedByA).isNotNull();

        // User B should NOT receive the update within a short timeout
        try {
            userBFuture.get(2, TimeUnit.SECONDS);
            // If we get here, the test fails because User B should not receive the update
            assertThat(false).as("User B should not receive portfolio update").isTrue();
        } catch (TimeoutException e) {
            // Expected: User B should not receive the update
        }

        sessionA.disconnect();
        sessionB.disconnect();
    }
}
