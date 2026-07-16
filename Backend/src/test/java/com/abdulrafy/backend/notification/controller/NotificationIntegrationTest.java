package com.abdulrafy.backend.notification.controller;

import com.abdulrafy.backend.IntegrationTestBase;
import com.abdulrafy.backend.auth.dto.AuthResponse;
import com.abdulrafy.backend.auth.dto.RegisterRequest;
import com.abdulrafy.backend.auth.entity.User;
import com.abdulrafy.backend.auth.repository.UserRepository;
import com.abdulrafy.backend.notification.entity.Notification;
import com.abdulrafy.backend.notification.repository.NotificationRepository;
import com.abdulrafy.backend.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class NotificationIntegrationTest extends IntegrationTestBase {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private AuthResponse authResponse;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        notificationRepository.deleteAllInBatch();

        String email = "notif-" + UUID.randomUUID() + "@test.com";
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest(email, "Password123!", "Notif User"))))
                .andExpect(status().isCreated())
                .andReturn();

        authResponse = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(), AuthResponse.class);

        User user = userRepository.findByEmail(email).orElseThrow();
        userId = user.getId();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test: GET /api/v1/notifications returns empty initially
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void getNotifications_empty_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test: POST a notification via service, verify it appears in GET
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void getNotifications_afterCreate_returnsNotification() throws Exception {
        notificationService.createNotification(userId, "TRADE_EXECUTED",
                "Trade Executed", "Buy 1 BTC", UUID.randomUUID(), "TRADE");

        mockMvc.perform(get("/api/v1/notifications")
                .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("TRADE_EXECUTED"))
                .andExpect(jsonPath("$.content[0].title").value("Trade Executed"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test: PATCH /api/v1/notifications/{id}/read marks as read
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void markAsRead_setsReadAt() throws Exception {
        var notif = notificationService.createNotification(userId, "TRADE_EXECUTED",
                "Trade Executed", "Buy 1 BTC", UUID.randomUUID(), "TRADE");

        mockMvc.perform(patch("/api/v1/notifications/" + notif.getId() + "/read")
                .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").isNotEmpty());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test: GET /api/v1/notifications/unread-count
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void unreadCount_returnsCorrectCount() throws Exception {
        notificationService.createNotification(userId, "TRADE_EXECUTED",
                "Trade 1", "Body", UUID.randomUUID(), "TRADE");
        notificationService.createNotification(userId, "TRADE_EXECUTED",
                "Trade 2", "Body", UUID.randomUUID(), "TRADE");

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test: Idempotent creation — duplicate type+referenceId is suppressed
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void createNotification_duplicateTypeAndReference_isIdempotent() {
        UUID refId = UUID.randomUUID();
        notificationService.createNotification(userId, "TRADE_EXECUTED",
                "Trade 1", "Body", refId, "TRADE");
        notificationService.createNotification(userId, "TRADE_EXECUTED",
                "Trade 2 DUPLICATE", "Body", refId, "TRADE");

        long count = notificationRepository.count();
        assert count == 1 : "Expected 1 notification, got " + count;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test: Cross-tenant isolation — User A cannot see User B's notifications
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void crossTenant_userACannotSeeUserBNotifications() throws Exception {
        // Create notification for User A
        notificationService.createNotification(userId, "TRADE_EXECUTED",
                "My Trade", "Body", UUID.randomUUID(), "TRADE");

        // Register User B
        String emailB = "notif-b-" + UUID.randomUUID() + "@test.com";
        MvcResult regB = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest(emailB, "Password123!", "User B"))))
                .andExpect(status().isCreated())
                .andReturn();
        AuthResponse authB = objectMapper.readValue(
                regB.getResponse().getContentAsString(), AuthResponse.class);

        // User B should see 0 notifications
        mockMvc.perform(get("/api/v1/notifications")
                .header("Authorization", "Bearer " + authB.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test: Unauthenticated request is rejected
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void getNotifications_unauthenticated_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }
}
