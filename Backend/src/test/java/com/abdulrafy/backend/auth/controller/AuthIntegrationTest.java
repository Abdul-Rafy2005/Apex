package com.abdulrafy.backend.auth.controller;

import com.abdulrafy.backend.IntegrationTestBase;
import com.abdulrafy.backend.auth.dto.*;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class AuthIntegrationTest extends IntegrationTestBase {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void register_login_refresh_flow() throws Exception {
        MockMvc mvc = mockMvc();
        RegisterRequest registerReq = new RegisterRequest("user1@test.com", "password123", "User One");

        MvcResult registerResult = mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("user1@test.com"))
                .andReturn();

        AuthResponse registerResponse = objectMapper.readValue(
            registerResult.getResponse().getContentAsString(), AuthResponse.class);

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new LoginRequest("user1@test.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new RefreshTokenRequest(registerResponse.refreshToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void register_duplicateEmail_rejected() throws Exception {
        MockMvc mvc = mockMvc();
        RegisterRequest req = new RegisterRequest("dup@test.com", "password123", "Dup User");

        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void login_invalidCredentials_rejected() throws Exception {
        mockMvc().perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new LoginRequest("nonexistent@test.com", "password123"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_wrongPassword_rejected() throws Exception {
        MockMvc mvc = mockMvc();
        RegisterRequest req = new RegisterRequest("wrongpw@test.com", "password123", "Wrong PW");
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new LoginRequest("wrongpw@test.com", "wrongpassword"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_invalidToken_rejected() throws Exception {
        mockMvc().perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new RefreshTokenRequest("invalid.token.here"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMe_withValidToken_returnsProfile() throws Exception {
        MockMvc mvc = mockMvc();
        RegisterRequest req = new RegisterRequest("profile@test.com", "password123", "Profile User");
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(
            result.getResponse().getContentAsString(), AuthResponse.class);

        mvc.perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer " + auth.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("profile@test.com"))
                .andExpect(jsonPath("$.displayName").value("Profile User"))
                .andExpect(jsonPath("$.portfolio.cashBalance").value(100000));
    }

    @Test
    void getMe_withoutToken_rejected() throws Exception {
        mockMvc().perform(get("/api/v1/users/me"))
                .andExpect(status().isForbidden());
    }
}
