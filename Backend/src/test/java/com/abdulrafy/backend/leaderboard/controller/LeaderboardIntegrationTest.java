package com.abdulrafy.backend.leaderboard.controller;

import com.abdulrafy.backend.IntegrationTestBase;
import com.abdulrafy.backend.auth.dto.AuthResponse;
import com.abdulrafy.backend.auth.dto.RegisterRequest;
import com.abdulrafy.backend.auth.entity.User;
import com.abdulrafy.backend.auth.entity.UserRole;
import com.abdulrafy.backend.auth.repository.UserRepository;
import com.abdulrafy.backend.leaderboard.service.LeaderboardService;
import com.abdulrafy.backend.organization.entity.Membership;
import com.abdulrafy.backend.organization.entity.Organization;
import com.abdulrafy.backend.organization.repository.MembershipRepository;
import com.abdulrafy.backend.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
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
class LeaderboardIntegrationTest extends IntegrationTestBase {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private LeaderboardService leaderboardService;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private AuthResponse authResponse;
    private Organization org;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });

        // Register user
        String email = "lb-" + UUID.randomUUID() + "@test.com";
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest(email, "Password123!", "LB User"))))
                .andExpect(status().isCreated())
                .andReturn();

        authResponse = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(), AuthResponse.class);

        User user = userRepository.findByEmail(email).orElseThrow();
        userId = user.getId();

        // Create org and membership
        org = organizationRepository.save(Organization.builder()
                .name("Test Org")
                .type("BOOTCAMP")
                .createdBy(user)
                .build());

        membershipRepository.save(Membership.builder()
                .user(user)
                .organization(org)
                .role(UserRole.TRADER)
                .build());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test: GET leaderboard returns entries
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void getLeaderboard_returnsEntries() throws Exception {
        leaderboardService.updateScore(org.getId(), userId, 5.25);

        mockMvc.perform(get("/api/v1/organizations/" + org.getId() + "/leaderboard")
                .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].score").value(5.25));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test: GET /me returns user's rank
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void getMyRank_returnsRank() throws Exception {
        leaderboardService.updateScore(org.getId(), userId, 10.0);

        mockMvc.perform(get("/api/v1/organizations/" + org.getId() + "/leaderboard/me")
                .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(1))
                .andExpect(jsonPath("$.score").value(10.0));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test: Leaderboard respects opt-out visibility
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void leaderboard_respectsOptOutVisibility() throws Exception {
        // User opts out
        User user = userRepository.findById(userId).orElseThrow();
        user.setLeaderboardVisible(false);
        userRepository.save(user);

        // Score update should remove from leaderboard
        leaderboardService.updateScore(org.getId(), userId, 5.25);

        mockMvc.perform(get("/api/v1/organizations/" + org.getId() + "/leaderboard")
                .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test: Cross-tenant — user cannot see another org's leaderboard
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void crossTenant_userCannotSeeOtherOrgLeaderboard() throws Exception {
        // Create a different org
        User otherUser = userRepository.save(User.builder()
                .email("other-" + UUID.randomUUID() + "@test.com")
                .passwordHash("hash")
                .displayName("Other")
                .role(UserRole.TRADER)
                .build());

        Organization otherOrg = organizationRepository.save(Organization.builder()
                .name("Other Org")
                .type("INDIVIDUAL")
                .createdBy(otherUser)
                .build());

        // User tries to view other org's leaderboard (not a member)
        mockMvc.perform(get("/api/v1/organizations/" + otherOrg.getId() + "/leaderboard")
                .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isForbidden());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test: Non-member cannot view leaderboard
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void nonMember_cannotViewLeaderboard() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/" + UUID.randomUUID() + "/leaderboard")
                .header("Authorization", "Bearer " + authResponse.accessToken()))
                .andExpect(status().isForbidden());
    }
}
