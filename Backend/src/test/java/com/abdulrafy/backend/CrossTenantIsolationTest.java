package com.abdulrafy.backend;

import com.abdulrafy.backend.auth.dto.AuthResponse;
import com.abdulrafy.backend.auth.dto.RegisterRequest;
import com.abdulrafy.backend.organization.dto.*;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class CrossTenantIsolationTest extends IntegrationTestBase {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    private AuthResponse registerUser(String email) throws Exception {
        MvcResult result = mockMvc().perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new RegisterRequest(email, "password123", "User " + email))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(
            result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private String createOrg(String token, String orgName) throws Exception {
        MvcResult result = mockMvc().perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateOrganizationRequest(orgName, "BOOTCAMP"))))
                .andExpect(status().isCreated())
                .andReturn();
        OrganizationResponse org = objectMapper.readValue(
            result.getResponse().getContentAsString(), OrganizationResponse.class);
        return org.id().toString();
    }

    @Test
    void userA_cannotAccess_userB_profile() throws Exception {
        AuthResponse userA = registerUser("usera@test.com");
        AuthResponse userB = registerUser("userb@test.com");

        mockMvc().perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer " + userA.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("usera@test.com"));

        mockMvc().perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer " + userB.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("userb@test.com"));
    }

    @Test
    void trader_cannotAccess_orgAdminEndpoints() throws Exception {
        AuthResponse trader = registerUser("trader@test.com");
        String orgId = createOrg(trader.accessToken(), "Trader Org");

        AuthResponse otherTrader = registerUser("othertrader@test.com");

        mockMvc().perform(get("/api/v1/organizations/" + orgId + "/members")
                .header("Authorization", "Bearer " + otherTrader.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void orgAdmin_canListOwnOrgMembers() throws Exception {
        AuthResponse admin = registerUser("admin@test.com");
        String orgId = createOrg(admin.accessToken(), "Admin Org");

        AuthResponse member = registerUser("member@test.com");
        mockMvc().perform(post("/api/v1/organizations/join")
                .header("Authorization", "Bearer " + member.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new JoinOrganizationRequest(java.util.UUID.fromString(orgId)))))
                .andExpect(status().isOk());

        mockMvc().perform(get("/api/v1/organizations/" + orgId + "/members")
                .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
