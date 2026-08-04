package com.handovercard.member;

import com.handovercard.auth.dto.LoginRequest;
import com.handovercard.auth.dto.SignupRequest;
import com.handovercard.member.dto.ChangePasswordRequest;
import com.handovercard.member.dto.UpdateProfileRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private record Session(String email, String accessToken) {
    }

    private String uniqueEmail(String label) {
        return label + "-" + System.nanoTime() + "@example.com";
    }

    private Session signupAndLogin(String label, String password) throws Exception {
        String email = uniqueEmail(label);
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(email, password, label))))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tokens = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Session(email, tokens.get("accessToken").asText());
    }

    @Test
    void getProfileReturnsTheAuthenticatedMember() throws Exception {
        Session session = signupAndLogin("profile-get", "password123");

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(session.email()))
                .andExpect(jsonPath("$.name").value("profile-get"));
    }

    @Test
    void gettingProfileWithoutAuthenticationIsRejected() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfileChangesTheName() throws Exception {
        Session session = signupAndLogin("profile-update", "password123");

        mockMvc.perform(patch("/api/members/me")
                        .contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + session.accessToken())
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest("New Name"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    void updateProfileWithBlankNameIsRejected() throws Exception {
        Session session = signupAndLogin("profile-blank", "password123");

        mockMvc.perform(patch("/api/members/me")
                        .contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + session.accessToken())
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest(""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePasswordAllowsLoginWithNewPasswordOnly() throws Exception {
        Session session = signupAndLogin("pw-change", "password123");

        mockMvc.perform(put("/api/members/me/password")
                        .contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + session.accessToken())
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest("password123", "new-password456"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(session.email(), "password123"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(session.email(), "new-password456"))))
                .andExpect(status().isOk());
    }

    @Test
    void changePasswordWithWrongCurrentPasswordIsRejected() throws Exception {
        Session session = signupAndLogin("pw-wrong", "password123");

        mockMvc.perform(put("/api/members/me/password")
                        .contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + session.accessToken())
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest("wrong-password", "new-password456"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteAccountRemovesTheMemberAndInvalidatesTheirToken() throws Exception {
        Session session = signupAndLogin("delete-account", "password123");

        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(session.email(), "password123"))))
                .andExpect(status().isUnauthorized());
    }
}
