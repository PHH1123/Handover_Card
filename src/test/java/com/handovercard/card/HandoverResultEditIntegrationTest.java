package com.handovercard.card;

import com.handovercard.auth.dto.LoginRequest;
import com.handovercard.auth.dto.SignupRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 작성자가 AI 결과를 고치는 경로. 카드가 COMPLETED가 되기까지 기다려야 하므로,
 * 파이프라인이 끝난 뒤에 수정을 건다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HandoverResultEditIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private record Session(String email, String name, String accessToken) {
    }

    // ---------- 헬퍼 ----------

    private Session signupAndLogin(String label) throws Exception {
        String email = label + "-" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/auth/signup").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(email, "password123", label))))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
        return new Session(email, label, token);
    }

    private long uploadCard(Session uploader, String receiverEmail) throws Exception {
        MockMultipartFile audio = new MockMultipartFile("audio", "note.wav", "audio/wav", "fake-audio".getBytes());
        MvcResult created = mockMvc.perform(multipart("/api/handover-cards").file(audio)
                        .param("senderName", uploader.name())
                        .param("receiverName", "받는사람")
                        .param("receiverEmail", receiverEmail == null ? "" : receiverEmail)
                        .param("sourceLanguage", "ko")
                        .param("targetLanguage", "en")
                        .header("Authorization", "Bearer " + uploader.accessToken()))
                .andExpect(status().isAccepted())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    /** 파이프라인이 끝나야 수정할 수 있으므로 COMPLETED가 될 때까지 기다린다. */
    private JsonNode awaitCompleted(long cardId, Session owner) throws Exception {
        Instant deadline = Instant.now().plus(TIMEOUT);
        JsonNode card = null;
        while (Instant.now().isBefore(deadline)) {
            MvcResult result = mockMvc.perform(get("/api/handover-cards/{id}", cardId)
                            .header("Authorization", "Bearer " + owner.accessToken()))
                    .andExpect(status().isOk())
                    .andReturn();
            card = objectMapper.readTree(result.getResponse().getContentAsString());
            if (card.get("status").asText().equals("COMPLETED")) {
                return card;
            }
            if (card.get("status").asText().equals("FAILED")) {
                throw new AssertionError("파이프라인이 실패했습니다: " + card.get("errorMessage"));
            }
            Thread.sleep(100);
        }
        throw new AssertionError("파이프라인이 " + TIMEOUT + " 안에 끝나지 않았습니다: " + card);
    }

    private long completedCard(Session owner, String receiverEmail) throws Exception {
        long cardId = uploadCard(owner, receiverEmail);
        awaitCompleted(cardId, owner);
        return cardId;
    }

    // ---------- 수정 ----------

    @Test
    void ownerCanRewriteTheTranscriptWithoutTouchingTheRest() throws Exception {
        Session owner = signupAndLogin("edit-owner");
        long cardId = completedCard(owner, null);
        String originalTranslation = awaitCompleted(cardId, owner).get("translatedText").asText();

        mockMvc.perform(patch("/api/handover-cards/{id}/result", cardId)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"transcript\":\"  손으로 고친 전사  \"}"))
                .andExpect(status().isOk())
                // 응답은 수정이 반영된 카드다
                .andExpect(jsonPath("$.transcript").value("손으로 고친 전사"))
                .andExpect(jsonPath("$.translatedText").value(originalTranslation));

        // 다시 조회해도 남아 있어야 한다
        mockMvc.perform(get("/api/handover-cards/{id}", cardId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcript").value("손으로 고친 전사"))
                .andExpect(jsonPath("$.summary.keyPoints").isNotEmpty());
    }

    @Test
    void ownerCanReplaceTheWholeSummary() throws Exception {
        Session owner = signupAndLogin("edit-summary");
        long cardId = completedCard(owner, null);

        mockMvc.perform(patch("/api/handover-cards/{id}/result", cardId)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"summary": {
                                   "keyPoints": [{"source": "고친 핵심", "target": "edited key point"},
                                                 {"source": "  ", "target": ""}],
                                   "actionItems": [{"source": "고친 할 일", "target": "edited action"}],
                                   "blockers": []
                                }}
                                """))
                .andExpect(status().isOk())
                // 두 칸이 모두 빈 항목은 저장되지 않는다
                .andExpect(jsonPath("$.summary.keyPoints.length()").value(1))
                .andExpect(jsonPath("$.summary.keyPoints[0].target").value("edited key point"))
                .andExpect(jsonPath("$.summary.actionItems[0].source").value("고친 할 일"))
                .andExpect(jsonPath("$.summary.blockers.length()").value(0))
                // 요약만 보냈으므로 전사는 그대로다
                .andExpect(jsonPath("$.transcript").isNotEmpty());
    }

    @Test
    void editingBumpsTheUpdatedTimestamp() throws Exception {
        Session owner = signupAndLogin("edit-stamp");
        long cardId = completedCard(owner, null);
        String before = awaitCompleted(cardId, owner).get("updatedAt").asText();

        Thread.sleep(10);
        MvcResult edited = mockMvc.perform(patch("/api/handover-cards/{id}/result", cardId)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"translatedText\":\"edited translation\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String after = objectMapper.readTree(edited.getResponse().getContentAsString()).get("updatedAt").asText();
        org.assertj.core.api.Assertions.assertThat(after).isNotEqualTo(before);
    }

    // ---------- 권한과 상태 ----------

    @Test
    void strangersGet404InsteadOfBeingToldTheCardExists() throws Exception {
        Session owner = signupAndLogin("edit-owner-guard");
        Session stranger = signupAndLogin("edit-stranger");
        long cardId = completedCard(owner, null);

        mockMvc.perform(patch("/api/handover-cards/{id}/result", cardId)
                        .header("Authorization", "Bearer " + stranger.accessToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"transcript\":\"남이 고침\"}"))
                .andExpect(status().isNotFound());
    }

    /** 수신자는 카드를 볼 수 있지만 결과를 고칠 수는 없다. */
    @Test
    void theReceiverCanReadTheCardButCannotEditIt() throws Exception {
        Session owner = signupAndLogin("edit-sender");
        Session receiver = signupAndLogin("edit-receiver");
        long cardId = completedCard(owner, receiver.email());

        mockMvc.perform(get("/api/handover-cards/{id}", cardId)
                        .header("Authorization", "Bearer " + receiver.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/handover-cards/{id}/result", cardId)
                        .header("Authorization", "Bearer " + receiver.accessToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"transcript\":\"수신자가 고침\"}"))
                .andExpect(status().isNotFound());
    }

    /**
     * 처리 중인 카드는 고칠 수 없다. 정상 경로로는 순식간에 COMPLETED가 되므로,
     * 상태만 되돌려 놓고 확인한다.
     */
    @Test
    void aCardThatIsNotCompletedCannotBeEdited() throws Exception {
        Session owner = signupAndLogin("edit-inprogress");
        long cardId = completedCard(owner, null);
        jdbcTemplate.update("UPDATE handover_cards SET status = 'SUMMARIZING' WHERE id = ?", cardId);

        mockMvc.perform(patch("/api/handover-cards/{id}/result", cardId)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"transcript\":\"처리 중 수정\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownCardIs404() throws Exception {
        Session owner = signupAndLogin("edit-missing");

        mockMvc.perform(patch("/api/handover-cards/{id}/result", 999_999L)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"transcript\":\"없는 카드\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void editingRequiresAuthentication() throws Exception {
        mockMvc.perform(patch("/api/handover-cards/{id}/result", 1L)
                        .contentType(APPLICATION_JSON)
                        .content("{\"transcript\":\"익명 수정\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 요청 값 검증 ----------

    @Test
    void anEmptyRequestIsRejectedInsteadOfSilentlyDoingNothing() throws Exception {
        Session owner = signupAndLogin("edit-empty");
        long cardId = completedCard(owner, null);

        mockMvc.perform(patch("/api/handover-cards/{id}/result", cardId)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    /** 빈 문자열은 "지우기"가 아니라 실수로 본다. 지우면 처리 전과 화면에서 구분되지 않는다. */
    @Test
    void blankTextIsRejected() throws Exception {
        Session owner = signupAndLogin("edit-blank");
        long cardId = completedCard(owner, null);

        mockMvc.perform(patch("/api/handover-cards/{id}/result", cardId)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"transcript\":\"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/handover-cards/{id}", cardId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcript").isNotEmpty());
    }
}
