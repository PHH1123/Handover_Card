package com.handovercard.card;

import com.handovercard.auth.dto.LoginRequest;
import com.handovercard.auth.dto.SignupRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
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

    private record Session(String email, String accessToken) {
    }

    private String uniqueEmail(String label) {
        return label + "-" + System.nanoTime() + "@example.com";
    }

    private Session signupAndLogin(String label) throws Exception {
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
        return new Session(email, tokens.get("accessToken").asText());
    }

    private Long createCard(String accessToken, String receiverEmail) throws Exception {
        MockMultipartFile audio = new MockMultipartFile("audio", "sample.wav", "audio/wav",
                "fake-audio".getBytes(StandardCharsets.UTF_8));
        MockMultipartHttpServletRequestBuilder request = multipart("/api/handover-cards")
                .file(audio)
                .param("senderName", "Alex")
                .param("receiverName", "Minji")
                .param("sourceLanguage", "en")
                .param("targetLanguage", "ko")
                .header("Authorization", "Bearer " + accessToken);
        if (receiverEmail != null) {
            request.param("receiverEmail", receiverEmail);
        }

        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isAccepted())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    @Test
    void ownerCanReadTheirOwnCard() throws Exception {
        Session owner = signupAndLogin("owner");
        Long cardId = createCard(owner.accessToken(), null);

        mockMvc.perform(get("/api/handover-cards/" + cardId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void anotherMemberCannotReadSomeoneElsesCard() throws Exception {
        Session owner = signupAndLogin("owner2");
        Long cardId = createCard(owner.accessToken(), null);

        Session stranger = signupAndLogin("stranger");

        mockMvc.perform(get("/api/handover-cards/" + cardId)
                        .header("Authorization", "Bearer " + stranger.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void linkedReceiverCanReadTheCardAddressedToThem() throws Exception {
        Session receiver = signupAndLogin("receiver");
        Session owner = signupAndLogin("sender");
        Long cardId = createCard(owner.accessToken(), receiver.email());

        mockMvc.perform(get("/api/handover-cards/" + cardId)
                        .header("Authorization", "Bearer " + receiver.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void unlinkedReceiverEmailIsIgnoredRatherThanFailingCreation() throws Exception {
        Session owner = signupAndLogin("owner3");

        Long cardId = createCard(owner.accessToken(), "not-signed-up-" + System.nanoTime() + "@example.com");

        mockMvc.perform(get("/api/handover-cards/" + cardId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void listReturnsOnlyCardsOwnedOrReceivedByRequester() throws Exception {
        Session receiver = signupAndLogin("list-receiver");
        Session owner = signupAndLogin("list-owner");
        Session stranger = signupAndLogin("list-stranger");

        Long ownedCardId = createCard(owner.accessToken(), null);
        Long receivedCardId = createCard(owner.accessToken(), receiver.email());
        createCard(stranger.accessToken(), null);

        MvcResult ownerResult = mockMvc.perform(get("/api/handover-cards")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode ownerCards = objectMapper.readTree(ownerResult.getResponse().getContentAsString());
        assertThat(ownerCards.size()).isEqualTo(2);

        MvcResult receiverResult = mockMvc.perform(get("/api/handover-cards")
                        .header("Authorization", "Bearer " + receiver.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode receiverCards = objectMapper.readTree(receiverResult.getResponse().getContentAsString());
        assertThat(receiverCards.size()).isEqualTo(1);
        assertThat(receiverCards.get(0).get("id").asLong()).isEqualTo(receivedCardId);
        assertThat(ownedCardId).isNotNull();
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

    @Test
    void listingCardsWithoutAuthenticationIsRejected() throws Exception {
        mockMvc.perform(get("/api/handover-cards"))
                .andExpect(status().isUnauthorized());
    }
}
