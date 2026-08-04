package com.handovercard.card;

import com.handovercard.auth.dto.LoginRequest;
import com.handovercard.auth.dto.SignupRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HandoverCardOwnershipIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String uniqueEmail(String label) {
        return label + "-" + System.nanoTime() + "@example.com";
    }

    private String signupAndLogin(String label) throws Exception {
        String email = uniqueEmail(label);
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(email, "password123", label))))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tokens = objectMapper.readTree(result.getResponse().getContentAsString());
        return tokens.get("accessToken").asText();
    }

    private Long createCard(String accessToken) throws Exception {
        MockMultipartFile audio = new MockMultipartFile("audio", "sample.wav", "audio/wav",
                "fake-audio".getBytes(StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(multipart("/api/handover-cards")
                        .file(audio)
                        .param("senderName", "Alex")
                        .param("receiverName", "Minji")
                        .param("sourceLanguage", "en")
                        .param("targetLanguage", "ko")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isAccepted())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    @Test
    void ownerCanReadTheirOwnCard() throws Exception {
        String accessToken = signupAndLogin("owner");
        Long cardId = createCard(accessToken);

        mockMvc.perform(get("/api/handover-cards/" + cardId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void anotherMemberCannotReadSomeoneElsesCard() throws Exception {
        String ownerToken = signupAndLogin("owner2");
        Long cardId = createCard(ownerToken);

        String strangerToken = signupAndLogin("stranger");

        mockMvc.perform(get("/api/handover-cards/" + cardId)
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void creatingACardWithoutAuthenticationIsRejected() throws Exception {
        MockMultipartFile audio = new MockMultipartFile("audio", "sample.wav", "audio/wav",
                "fake-audio".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/handover-cards")
                        .file(audio)
                        .param("senderName", "Alex")
                        .param("receiverName", "Minji")
                        .param("sourceLanguage", "en")
                        .param("targetLanguage", "ko"))
                .andExpect(status().isUnauthorized());
    }
}
