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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 업로드한 음성이 비동기 파이프라인을 끝까지 통과하는지 확인한다.
 *
 * <p>파이프라인은 예외를 잡아 카드를 FAILED로 기록하므로, 저장소에서 음성을 못 읽어도 요청은 202로
 * 성공한다. 상태를 강제로 세팅하는 기존 테스트만으로는 저장소 → 전사 연결이 끊겨도 알 수 없어서,
 * 여기서는 실제로 COMPLETED에 도달하는지를 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HandoverPipelineIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String signupAndLogin(String label) throws Exception {
        String email = label + "-" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/auth/signup").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(email, "password123", label))))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private JsonNode awaitSettled(long cardId, String token) throws Exception {
        Instant deadline = Instant.now().plus(TIMEOUT);
        JsonNode card = null;
        while (Instant.now().isBefore(deadline)) {
            MvcResult result = mockMvc.perform(get("/api/handover-cards/{id}", cardId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            card = objectMapper.readTree(result.getResponse().getContentAsString());
            String state = card.get("status").asText();
            if (state.equals("COMPLETED") || state.equals("FAILED")) {
                return card;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("파이프라인이 " + TIMEOUT + " 안에 끝나지 않았습니다: " + card);
    }

    @Test
    void uploadedAudioRunsThroughTranscriptionAndSummarization() throws Exception {
        String token = signupAndLogin("pipeline");
        MockMultipartFile audio = new MockMultipartFile("audio", "note.wav", "audio/wav", "fake-audio".getBytes());

        MvcResult created = mockMvc.perform(multipart("/api/handover-cards").file(audio)
                        .param("senderName", "보내는사람")
                        .param("receiverName", "받는사람")
                        .param("sourceLanguage", "ko")
                        .param("targetLanguage", "en")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn();
        long cardId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        JsonNode card = awaitSettled(cardId, token);

        // 실패했다면 원인을 그대로 보여준다 — 저장소에서 음성을 못 읽은 경우가 여기로 떨어진다
        assertThat(card.get("status").asText())
                .withFailMessage("파이프라인이 실패했습니다: %s", card.get("errorMessage"))
                .isEqualTo("COMPLETED");

        assertThat(card.get("transcript").asText()).isNotBlank();
        assertThat(card.get("translatedText").asText()).isNotBlank();
        assertThat(card.get("summary").get("keyPoints")).isNotEmpty();
    }

    @Test
    void transcriptionSeesTheStoredFileName() throws Exception {
        String token = signupAndLogin("pipeline-name");
        MockMultipartFile audio = new MockMultipartFile("audio", "note.wav", "audio/wav", "fake-audio".getBytes());

        MvcResult created = mockMvc.perform(multipart("/api/handover-cards").file(audio)
                        .param("senderName", "보내는사람")
                        .param("receiverName", "받는사람")
                        .param("sourceLanguage", "ko")
                        .param("targetLanguage", "en")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn();
        long cardId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        JsonNode card = awaitSettled(cardId, token);

        // 저장소가 건네준 Resource에서 파일 이름을 읽어오는지 (Path 시절 getFileName()이 하던 일)
        assertThat(card.get("transcript").asText())
                .contains(String.valueOf(cardId))
                .contains(".wav");
    }
}
