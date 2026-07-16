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
    void register_setsRefreshTokenCookie() throws Exception {
        MockMvc mvc = mockMvc();
        RegisterRequest req = new RegisterRequest("cookie@test.com", "password123", "Cookie User");

        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("cookie@test.com"))
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(header().string("Set-Cookie",
                    org.hamcrest.Matchers.containsString("refresh_token=")))
                .andExpect(header().string("Set-Cookie",
                    org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie",
                    org.hamcrest.Matchers.containsString("Secure")))
                .andExpect(header().string("Set-Cookie",
                    org.hamcrest.Matchers.containsString("SameSite=None")));
    }

    @Test
    void login_setsRefreshTokenCookie() throws Exception {
        MockMvc mvc = mockMvc();
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new RegisterRequest("logincookie@test.com", "password123", "Login Cookie"))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new LoginRequest("logincookie@test.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(header().string("Set-Cookie",
                    org.hamcrest.Matchers.containsString("refresh_token=")))
                .andExpect(header().string("Set-Cookie",
                    org.hamcrest.Matchers.containsString("HttpOnly")));
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
                .andExpect(jsonPath("$.user.email").value("user1@test.com"))
                .andReturn();

        // Extract refresh token from Set-Cookie header
        String setCookie = registerResult.getResponse().getHeader("Set-Cookie");
        String refreshToken = setCookie.split("refresh_token=")[1].split(";")[0];

        // Use refresh token cookie to get new access token
        mvc.perform(post("/api/v1/auth/refresh")
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(header().string("Set-Cookie",
                    org.hamcrest.Matchers.containsString("refresh_token=")));
    }

    @Test
    void refresh_withoutCookie_rejected() throws Exception {
        mockMvc().perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withInvalidCookie_rejected() throws Exception {
        mockMvc().perform(post("/api/v1/auth/refresh")
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", "invalid.token.here")))
                .andExpect(status().isUnauthorized());
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
                .andExpect(status().isUnauthorized());
    }
}
