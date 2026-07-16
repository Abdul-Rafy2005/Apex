package com.abdulrafy.backend.organization.controller;

import com.abdulrafy.backend.IntegrationTestBase;
import com.abdulrafy.backend.auth.dto.*;
import com.abdulrafy.backend.organization.dto.*;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class OrganizationIntegrationTest extends IntegrationTestBase {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    private AuthResponse registerUser(String email) throws Exception {
        MvcResult result = mockMvc().perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new RegisterRequest(email, "password123", "User"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(
            result.getResponse().getContentAsString(), AuthResponse.class);
    }

    @Test
    void createOrganization_returnsCreated() throws Exception {
        AuthResponse user = registerUser("creator@test.com");

        mockMvc().perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + user.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateOrganizationRequest("Test Org", "BOOTCAMP"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Org"))
                .andExpect(jsonPath("$.type").value("BOOTCAMP"));
    }

    @Test
    void joinOrganization_returnsOk() throws Exception {
        AuthResponse creator = registerUser("joincreator@test.com");
        MvcResult orgResult = mockMvc().perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + creator.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateOrganizationRequest("Join Org", "UNIVERSITY"))))
                .andExpect(status().isCreated())
                .andReturn();
        OrganizationResponse org = objectMapper.readValue(
            orgResult.getResponse().getContentAsString(), OrganizationResponse.class);

        AuthResponse joiner = registerUser("joiner@test.com");
        mockMvc().perform(post("/api/v1/organizations/join")
                .header("Authorization", "Bearer " + joiner.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new JoinOrganizationRequest(org.id()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TRADER"));
    }

    @Test
    void joinOrganization_duplicateJoin_rejected() throws Exception {
        AuthResponse creator = registerUser("dupcreator@test.com");
        MvcResult orgResult = mockMvc().perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + creator.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateOrganizationRequest("Dup Org", "INDIVIDUAL"))))
                .andExpect(status().isCreated())
                .andReturn();
        OrganizationResponse org = objectMapper.readValue(
            orgResult.getResponse().getContentAsString(), OrganizationResponse.class);

        AuthResponse joiner = registerUser("dupjoiner@test.com");
        mockMvc().perform(post("/api/v1/organizations/join")
                .header("Authorization", "Bearer " + joiner.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new JoinOrganizationRequest(org.id()))))
                .andExpect(status().isOk());

        mockMvc().perform(post("/api/v1/organizations/join")
                .header("Authorization", "Bearer " + joiner.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new JoinOrganizationRequest(org.id()))))
                .andExpect(status().isConflict());
    }

    @Test
    void listMembers_orgAdminCanList() throws Exception {
        AuthResponse admin = registerUser("orgadmin@test.com");
        MvcResult orgResult = mockMvc().perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + admin.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateOrganizationRequest("Admin Org", "BOOTCAMP"))))
                .andExpect(status().isCreated())
                .andReturn();
        OrganizationResponse org = objectMapper.readValue(
            orgResult.getResponse().getContentAsString(), OrganizationResponse.class);

        AuthResponse member = registerUser("orgmember@test.com");
        mockMvc().perform(post("/api/v1/organizations/join")
                .header("Authorization", "Bearer " + member.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new JoinOrganizationRequest(org.id()))))
                .andExpect(status().isOk());

        mockMvc().perform(get("/api/v1/organizations/" + org.id() + "/members")
                .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listMembers_nonMember_rejected() throws Exception {
        AuthResponse admin = registerUser("listadmin@test.com");
        MvcResult orgResult = mockMvc().perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + admin.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateOrganizationRequest("List Org", "INDIVIDUAL"))))
                .andExpect(status().isCreated())
                .andReturn();
        OrganizationResponse org = objectMapper.readValue(
            orgResult.getResponse().getContentAsString(), OrganizationResponse.class);

        AuthResponse outsider = registerUser("outsider@test.com");
        mockMvc().perform(get("/api/v1/organizations/" + org.id() + "/members")
                .header("Authorization", "Bearer " + outsider.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listMembers_traderMember_forbidden() throws Exception {
        AuthResponse admin = registerUser("traderlistadmin@test.com");
        MvcResult orgResult = mockMvc().perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + admin.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateOrganizationRequest("Trader List Org", "BOOTCAMP"))))
                .andExpect(status().isCreated())
                .andReturn();
        OrganizationResponse org = objectMapper.readValue(
            orgResult.getResponse().getContentAsString(), OrganizationResponse.class);

        AuthResponse trader = registerUser("traderlistmember@test.com");
        mockMvc().perform(post("/api/v1/organizations/join")
                .header("Authorization", "Bearer " + trader.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new JoinOrganizationRequest(org.id()))))
                .andExpect(status().isOk());

        mockMvc().perform(get("/api/v1/organizations/" + org.id() + "/members")
                .header("Authorization", "Bearer " + trader.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateMemberRole_traderMember_forbidden() throws Exception {
        AuthResponse admin = registerUser("roleadmin@test.com");
        MvcResult orgResult = mockMvc().perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + admin.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateOrganizationRequest("Role Org", "BOOTCAMP"))))
                .andExpect(status().isCreated())
                .andReturn();
        OrganizationResponse org = objectMapper.readValue(
            orgResult.getResponse().getContentAsString(), OrganizationResponse.class);

        AuthResponse trader = registerUser("roletrader@test.com");
        MvcResult joinResult = mockMvc().perform(post("/api/v1/organizations/join")
                .header("Authorization", "Bearer " + trader.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new JoinOrganizationRequest(org.id()))))
                .andExpect(status().isOk())
                .andReturn();
        MembershipResponse membership = objectMapper.readValue(
            joinResult.getResponse().getContentAsString(), MembershipResponse.class);

        mockMvc().perform(put("/api/v1/organizations/" + org.id() + "/members/" + membership.userId() + "/role")
                .header("Authorization", "Bearer " + trader.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new UpdateMembershipRoleRequest(com.abdulrafy.backend.auth.entity.UserRole.INSTRUCTOR))))
                .andExpect(status().isForbidden());
    }

    @Test
    void removeMember_traderMember_forbidden() throws Exception {
        AuthResponse admin = registerUser("removeadmin@test.com");
        MvcResult orgResult = mockMvc().perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + admin.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateOrganizationRequest("Remove Org", "BOOTCAMP"))))
                .andExpect(status().isCreated())
                .andReturn();
        OrganizationResponse org = objectMapper.readValue(
            orgResult.getResponse().getContentAsString(), OrganizationResponse.class);

        AuthResponse trader = registerUser("removetrader@test.com");
        mockMvc().perform(post("/api/v1/organizations/join")
                .header("Authorization", "Bearer " + trader.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new JoinOrganizationRequest(org.id()))))
                .andExpect(status().isOk());

        AuthResponse target = registerUser("removetarget@test.com");
        MvcResult targetJoinResult = mockMvc().perform(post("/api/v1/organizations/join")
                .header("Authorization", "Bearer " + target.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new JoinOrganizationRequest(org.id()))))
                .andExpect(status().isOk())
                .andReturn();
        MembershipResponse targetMembership = objectMapper.readValue(
            targetJoinResult.getResponse().getContentAsString(), MembershipResponse.class);

        mockMvc().perform(delete("/api/v1/organizations/" + org.id() + "/members/" + targetMembership.userId())
                .header("Authorization", "Bearer " + trader.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditLog_traderMember_forbidden() throws Exception {
        AuthResponse admin = registerUser("auditadmin@test.com");
        MvcResult orgResult = mockMvc().perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + admin.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateOrganizationRequest("Audit Org", "BOOTCAMP"))))
                .andExpect(status().isCreated())
                .andReturn();
        OrganizationResponse org = objectMapper.readValue(
            orgResult.getResponse().getContentAsString(), OrganizationResponse.class);

        AuthResponse trader = registerUser("audittrader@test.com");
        mockMvc().perform(post("/api/v1/organizations/join")
                .header("Authorization", "Bearer " + trader.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new JoinOrganizationRequest(org.id()))))
                .andExpect(status().isOk());

        mockMvc().perform(get("/api/v1/organizations/" + org.id() + "/audit-log")
                .header("Authorization", "Bearer " + trader.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateMemberRole_instructor_forbidden() throws Exception {
        AuthResponse admin = registerUser("instadmin@test.com");
        MvcResult orgResult = mockMvc().perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + admin.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateOrganizationRequest("Inst Org", "BOOTCAMP"))))
                .andExpect(status().isCreated())
                .andReturn();
        OrganizationResponse org = objectMapper.readValue(
            orgResult.getResponse().getContentAsString(), OrganizationResponse.class);

        AuthResponse instructor = registerUser("instructor@test.com");
        mockMvc().perform(post("/api/v1/organizations/join")
                .header("Authorization", "Bearer " + instructor.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new JoinOrganizationRequest(org.id()))))
                .andExpect(status().isOk());

        AuthResponse trader = registerUser("insttrader@test.com");
        MvcResult traderJoinResult = mockMvc().perform(post("/api/v1/organizations/join")
                .header("Authorization", "Bearer " + trader.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new JoinOrganizationRequest(org.id()))))
                .andExpect(status().isOk())
                .andReturn();
        MembershipResponse traderMembership = objectMapper.readValue(
            traderJoinResult.getResponse().getContentAsString(), MembershipResponse.class);

        mockMvc().perform(put("/api/v1/organizations/" + org.id() + "/members/" + traderMembership.userId() + "/role")
                .header("Authorization", "Bearer " + instructor.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new UpdateMembershipRoleRequest(com.abdulrafy.backend.auth.entity.UserRole.ORG_ADMIN))))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditLog_instructor_forbidden() throws Exception {
        AuthResponse admin = registerUser("auditinstadmin@test.com");
        MvcResult orgResult = mockMvc().perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + admin.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateOrganizationRequest("Audit Inst Org", "BOOTCAMP"))))
                .andExpect(status().isCreated())
                .andReturn();
        OrganizationResponse org = objectMapper.readValue(
            orgResult.getResponse().getContentAsString(), OrganizationResponse.class);

        AuthResponse instructor = registerUser("auditinstructor@test.com");
        mockMvc().perform(post("/api/v1/organizations/join")
                .header("Authorization", "Bearer " + instructor.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new JoinOrganizationRequest(org.id()))))
                .andExpect(status().isOk());

        mockMvc().perform(get("/api/v1/organizations/" + org.id() + "/audit-log")
                .header("Authorization", "Bearer " + instructor.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateMemberRole_orgAdmin_canPromote() throws Exception {
        AuthResponse admin = registerUser("promoteadmin@test.com");
        MvcResult orgResult = mockMvc().perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + admin.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateOrganizationRequest("Promote Org", "BOOTCAMP"))))
                .andExpect(status().isCreated())
                .andReturn();
        OrganizationResponse org = objectMapper.readValue(
            orgResult.getResponse().getContentAsString(), OrganizationResponse.class);

        AuthResponse trader = registerUser("promotetrader@test.com");
        MvcResult joinResult = mockMvc().perform(post("/api/v1/organizations/join")
                .header("Authorization", "Bearer " + trader.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new JoinOrganizationRequest(org.id()))))
                .andExpect(status().isOk())
                .andReturn();
        MembershipResponse membership = objectMapper.readValue(
            joinResult.getResponse().getContentAsString(), MembershipResponse.class);

        mockMvc().perform(put("/api/v1/organizations/" + org.id() + "/members/" + membership.userId() + "/role")
                .header("Authorization", "Bearer " + admin.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new UpdateMembershipRoleRequest(com.abdulrafy.backend.auth.entity.UserRole.INSTRUCTOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("INSTRUCTOR"));
    }

    @Test
    void updateMemberRole_lastAdmin_demoteForbidden() throws Exception {
        AuthResponse admin = registerUser("lastadmin@test.com");
        MvcResult orgResult = mockMvc().perform(post("/api/v1/organizations")
                .header("Authorization", "Bearer " + admin.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateOrganizationRequest("Last Admin Org", "BOOTCAMP"))))
                .andExpect(status().isCreated())
                .andReturn();
        OrganizationResponse org = objectMapper.readValue(
            orgResult.getResponse().getContentAsString(), OrganizationResponse.class);

        mockMvc().perform(put("/api/v1/organizations/" + org.id() + "/members/" + admin.user().id() + "/role")
                .header("Authorization", "Bearer " + admin.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new UpdateMembershipRoleRequest(com.abdulrafy.backend.auth.entity.UserRole.TRADER))))
                .andExpect(status().isConflict());
    }
}
