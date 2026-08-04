package com.handovercard.auth;

import com.handovercard.auth.dto.LoginRequest;
import com.handovercard.auth.dto.RefreshRequest;
import com.handovercard.auth.dto.SignupRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String uniqueEmail(String label) {
        return label + "-" + System.nanoTime() + "@example.com";
    }

    private void signup(String email, String password, String name) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(email, password, name))))
                .andExpect(status().isCreated());
    }

    private JsonNode login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void unauthenticatedRequestToProtectedEndpointIsRejected() throws Exception {
        mockMvc.perform(get("/api/handover-cards/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signupThenLoginIssuesTokens() throws Exception {
        String email = uniqueEmail("signup");
        signup(email, "password123", "Alex");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void signupWithDuplicateEmailIsRejected() throws Exception {
        String email = uniqueEmail("dup");
        signup(email, "password123", "Alex");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(email, "password123", "Alex"))))
                .andExpect(status().isConflict());
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        String email = uniqueEmail("badpw");
        signup(email, "password123", "Alex");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTokenAndRejectsReuseOfOldToken() throws Exception {
        String email = uniqueEmail("refresh");
        signup(email, "password123", "Alex");
        JsonNode tokens = login(email, "password123");
        String oldRefreshToken = tokens.get("refreshToken").asText();
        String refreshBody = objectMapper.writeValueAsString(new RefreshRequest(oldRefreshToken));

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode newTokens = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        assertThat(newTokens.get("refreshToken").asText()).isNotEqualTo(oldRefreshToken);

        // the rotated-out refresh token must be rejected on reuse
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshTokenForFutureUse() throws Exception {
        String email = uniqueEmail("logout");
        signup(email, "password123", "Alex");
        JsonNode tokens = login(email, "password123");
        String refreshBody = objectMapper.writeValueAsString(new RefreshRequest(tokens.get("refreshToken").asText()));

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isUnauthorized());
    }
}
