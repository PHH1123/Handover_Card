package com.handovercard.team;

import com.handovercard.auth.dto.LoginRequest;
import com.handovercard.auth.dto.SignupRequest;
import com.handovercard.team.dto.CreateTeamRequest;
import com.handovercard.team.dto.TransferLeadershipRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeamIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private record Session(String email, String name, Long memberId, String accessToken) {
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

        MvcResult profile = mockMvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        Long id = objectMapper.readTree(profile.getResponse().getContentAsString()).get("id").asLong();
        return new Session(email, label, id, token);
    }

    private Long createTeam(Session session, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/teams").header("Authorization", "Bearer " + session.accessToken())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTeamRequest(name))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void apply(Session session, Long teamId) throws Exception {
        mockMvc.perform(post("/api/teams/{id}/join-requests", teamId)
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isCreated());
    }

    private Long firstPendingRequestId(Session leader) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/teams/me/join-requests")
                        .header("Authorization", "Bearer " + leader.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode requests = objectMapper.readTree(result.getResponse().getContentAsString());
        return requests.get(0).get("id").asLong();
    }

    private String uniqueTeamName(String label) {
        return label + "-" + System.nanoTime();
    }

    /** 팀장 승인까지 마쳐 실제 팀원으로 만든다. */
    private void joinTeam(Session member, Session leader, Long teamId) throws Exception {
        apply(member, teamId);
        Long requestId = firstPendingRequestId(leader);
        mockMvc.perform(post("/api/teams/join-requests/{id}/approve", requestId)
                        .header("Authorization", "Bearer " + leader.accessToken()))
                .andExpect(status().isNoContent());
    }

    // ---------- 팀 생성 ----------

    @Test
    void creatingATeamMakesTheCreatorItsLeader() throws Exception {
        Session leader = signupAndLogin("leader");
        createTeam(leader, uniqueTeamName("team"));

        mockMvc.perform(get("/api/teams/me").header("Authorization", "Bearer " + leader.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leader").value(true))
                .andExpect(jsonPath("$.leaderEmail").value(leader.email()))
                .andExpect(jsonPath("$.members.length()").value(1));
    }

    @Test
    void memberAlreadyOnATeamCannotCreateAnother() throws Exception {
        Session leader = signupAndLogin("dup-create");
        createTeam(leader, uniqueTeamName("first"));

        mockMvc.perform(post("/api/teams").header("Authorization", "Bearer " + leader.accessToken())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTeamRequest(uniqueTeamName("second")))))
                .andExpect(status().isConflict());
    }

    @Test
    void teamNamesMustBeUnique() throws Exception {
        Session first = signupAndLogin("name-owner");
        Session second = signupAndLogin("name-taker");
        String name = uniqueTeamName("shared");
        createTeam(first, name);

        mockMvc.perform(post("/api/teams").header("Authorization", "Bearer " + second.accessToken())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTeamRequest(name))))
                .andExpect(status().isConflict());
    }

    @Test
    void memberWithoutATeamGets404() throws Exception {
        Session loner = signupAndLogin("no-team");

        mockMvc.perform(get("/api/teams/me").header("Authorization", "Bearer " + loner.accessToken()))
                .andExpect(status().isNotFound());
    }

    // ---------- 가입 신청과 승인 ----------

    @Test
    void approvedApplicantBecomesATeamMember() throws Exception {
        Session leader = signupAndLogin("approve-leader");
        Session applicant = signupAndLogin("approve-applicant");
        Long teamId = createTeam(leader, uniqueTeamName("approve"));

        joinTeam(applicant, leader, teamId);

        mockMvc.perform(get("/api/teams/me").header("Authorization", "Bearer " + applicant.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leader").value(false))
                .andExpect(jsonPath("$.members.length()").value(2));
    }

    @Test
    void rejectedApplicantDoesNotJoin() throws Exception {
        Session leader = signupAndLogin("reject-leader");
        Session applicant = signupAndLogin("reject-applicant");
        Long teamId = createTeam(leader, uniqueTeamName("reject"));
        apply(applicant, teamId);

        mockMvc.perform(post("/api/teams/join-requests/{id}/reject", firstPendingRequestId(leader))
                        .header("Authorization", "Bearer " + leader.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/teams/me").header("Authorization", "Bearer " + applicant.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicatePendingApplicationIsRejected() throws Exception {
        Session leader = signupAndLogin("dup-apply-leader");
        Session applicant = signupAndLogin("dup-apply");
        Long teamId = createTeam(leader, uniqueTeamName("dup-apply"));
        apply(applicant, teamId);

        mockMvc.perform(post("/api/teams/{id}/join-requests", teamId)
                        .header("Authorization", "Bearer " + applicant.accessToken()))
                .andExpect(status().isConflict());
    }

    @Test
    void nonLeaderCannotApproveAndTheRequestStaysPending() throws Exception {
        Session leader = signupAndLogin("guard-leader");
        Session applicant = signupAndLogin("guard-applicant");
        Session outsider = signupAndLogin("guard-outsider");
        Long teamId = createTeam(leader, uniqueTeamName("guard"));
        apply(applicant, teamId);
        Long requestId = firstPendingRequestId(leader);

        // 다른 팀의 신청이 존재한다는 사실을 숨기려고 403이 아닌 404를 준다
        mockMvc.perform(post("/api/teams/join-requests/{id}/approve", requestId)
                        .header("Authorization", "Bearer " + outsider.accessToken()))
                .andExpect(status().isNotFound());

        // 신청은 그대로 대기 상태여야 한다
        mockMvc.perform(get("/api/teams/me/join-requests").header("Authorization", "Bearer " + leader.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void alreadyProcessedRequestCannotBeApprovedAgain() throws Exception {
        Session leader = signupAndLogin("twice-leader");
        Session applicant = signupAndLogin("twice-applicant");
        Long teamId = createTeam(leader, uniqueTeamName("twice"));
        apply(applicant, teamId);
        Long requestId = firstPendingRequestId(leader);

        mockMvc.perform(post("/api/teams/join-requests/{id}/approve", requestId)
                        .header("Authorization", "Bearer " + leader.accessToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/teams/join-requests/{id}/approve", requestId)
                        .header("Authorization", "Bearer " + leader.accessToken()))
                .andExpect(status().isConflict());
    }

    @Test
    void plainMemberCannotListJoinRequests() throws Exception {
        Session leader = signupAndLogin("list-leader");
        Session member = signupAndLogin("list-member");
        Long teamId = createTeam(leader, uniqueTeamName("list"));
        joinTeam(member, leader, teamId);

        mockMvc.perform(get("/api/teams/me/join-requests").header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isConflict());
    }

    // ---------- 팀장 위임 ----------

    @Test
    void leadershipTransferSwapsRolesAndKeepsBothOnTheTeam() throws Exception {
        Session leader = signupAndLogin("transfer-leader");
        Session member = signupAndLogin("transfer-member");
        Long teamId = createTeam(leader, uniqueTeamName("transfer"));
        joinTeam(member, leader, teamId);

        mockMvc.perform(put("/api/teams/me/leader").header("Authorization", "Bearer " + leader.accessToken())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransferLeadershipRequest(member.memberId()))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/teams/me").header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leader").value(true))
                .andExpect(jsonPath("$.leaderEmail").value(member.email()));

        // 넘긴 사람은 팀에 남되 일반 팀원이 된다
        mockMvc.perform(get("/api/teams/me").header("Authorization", "Bearer " + leader.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leader").value(false))
                .andExpect(jsonPath("$.members.length()").value(2));
    }

    @Test
    void leadershipCannotBeTransferredToSomeoneOutsideTheTeam() throws Exception {
        Session leader = signupAndLogin("transfer-guard-leader");
        Session outsider = signupAndLogin("transfer-guard-outsider");
        createTeam(leader, uniqueTeamName("transfer-guard"));

        mockMvc.perform(put("/api/teams/me/leader").header("Authorization", "Bearer " + leader.accessToken())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransferLeadershipRequest(outsider.memberId()))))
                .andExpect(status().isConflict());
    }

    @Test
    void plainMemberCannotTransferLeadership() throws Exception {
        Session leader = signupAndLogin("transfer-deny-leader");
        Session member = signupAndLogin("transfer-deny-member");
        Long teamId = createTeam(leader, uniqueTeamName("transfer-deny"));
        joinTeam(member, leader, teamId);

        mockMvc.perform(put("/api/teams/me/leader").header("Authorization", "Bearer " + member.accessToken())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransferLeadershipRequest(leader.memberId()))))
                .andExpect(status().isConflict());
    }

    // ---------- 추방 ----------

    @Test
    void leaderCanKickAMemberAndTheyCanApplyAgain() throws Exception {
        Session leader = signupAndLogin("kick-leader");
        Session member = signupAndLogin("kick-member");
        Long teamId = createTeam(leader, uniqueTeamName("kick"));
        joinTeam(member, leader, teamId);

        mockMvc.perform(delete("/api/teams/me/members/{id}", member.memberId())
                        .header("Authorization", "Bearer " + leader.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/teams/me").header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isNotFound());

        // 추방된 회원은 다시 신청할 수 있어야 한다
        apply(member, teamId);
    }

    @Test
    void leaderCannotKickThemselves() throws Exception {
        Session leader = signupAndLogin("self-kick");
        createTeam(leader, uniqueTeamName("self-kick"));

        mockMvc.perform(delete("/api/teams/me/members/{id}", leader.memberId())
                        .header("Authorization", "Bearer " + leader.accessToken()))
                .andExpect(status().isConflict());
    }

    @Test
    void plainMemberCannotKickAnyone() throws Exception {
        Session leader = signupAndLogin("kick-deny-leader");
        Session member = signupAndLogin("kick-deny-member");
        Long teamId = createTeam(leader, uniqueTeamName("kick-deny"));
        joinTeam(member, leader, teamId);

        mockMvc.perform(delete("/api/teams/me/members/{id}", leader.memberId())
                        .header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isConflict());
    }

    // ---------- 팀 나가기 / 팀 삭제 ----------

    @Test
    void plainMemberCanLeaveButLeaderCannot() throws Exception {
        Session leader = signupAndLogin("leave-leader");
        Session member = signupAndLogin("leave-member");
        Long teamId = createTeam(leader, uniqueTeamName("leave"));
        joinTeam(member, leader, teamId);

        mockMvc.perform(delete("/api/teams/me/membership").header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/teams/me/membership").header("Authorization", "Bearer " + leader.accessToken()))
                .andExpect(status().isConflict());
    }

    @Test
    void deletingATeamReleasesEveryMember() throws Exception {
        Session leader = signupAndLogin("delete-leader");
        Session member = signupAndLogin("delete-member");
        Long teamId = createTeam(leader, uniqueTeamName("delete"));
        joinTeam(member, leader, teamId);

        mockMvc.perform(delete("/api/teams/me").header("Authorization", "Bearer " + leader.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/teams/me").header("Authorization", "Bearer " + leader.accessToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/teams/me").header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void plainMemberCannotDeleteTheTeam() throws Exception {
        Session leader = signupAndLogin("delete-deny-leader");
        Session member = signupAndLogin("delete-deny-member");
        Long teamId = createTeam(leader, uniqueTeamName("delete-deny"));
        joinTeam(member, leader, teamId);

        mockMvc.perform(delete("/api/teams/me").header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isConflict());
    }

    // ---------- 계정 삭제와의 연동 (회귀 방지) ----------

    @Test
    void memberWithJoinHistoryCanDeleteTheirAccount() throws Exception {
        Session leader = signupAndLogin("history-leader");
        Session applicant = signupAndLogin("history-applicant");
        Long teamId = createTeam(leader, uniqueTeamName("history"));
        apply(applicant, teamId);

        // 가입 신청 기록이 회원을 참조하므로 함께 정리되어야 한다
        mockMvc.perform(delete("/api/members/me").header("Authorization", "Bearer " + applicant.accessToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void leaderMustClearTheirTeamBeforeDeletingTheirAccount() throws Exception {
        Session leader = signupAndLogin("leader-delete");
        createTeam(leader, uniqueTeamName("leader-delete"));

        mockMvc.perform(delete("/api/members/me").header("Authorization", "Bearer " + leader.accessToken()))
                .andExpect(status().isConflict());

        // 팀을 없앤 뒤에는 탈퇴할 수 있다
        mockMvc.perform(delete("/api/teams/me").header("Authorization", "Bearer " + leader.accessToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/members/me").header("Authorization", "Bearer " + leader.accessToken()))
                .andExpect(status().isNoContent());
    }

    // ---------- 같은 팀 이메일 조회 ----------

    @Test
    void lookupOnlyReturnsTeammates() throws Exception {
        Session leader = signupAndLogin("lookup-leader");
        Session teammate = signupAndLogin("lookup-teammate");
        Session outsider = signupAndLogin("lookup-outsider");
        Long teamId = createTeam(leader, uniqueTeamName("lookup"));
        joinTeam(teammate, leader, teamId);

        mockMvc.perform(get("/api/members/lookup").param("name", teammate.name())
                        .header("Authorization", "Bearer " + leader.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasTeam").value(true))
                .andExpect(jsonPath("$.matches.length()").value(1))
                .andExpect(jsonPath("$.matches[0].email").value(teammate.email()));

        // 팀 밖의 회원은 이름이 정확히 맞아도 나오지 않는다
        mockMvc.perform(get("/api/members/lookup").param("name", outsider.name())
                        .header("Authorization", "Bearer " + leader.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches.length()").value(0));
    }

    @Test
    void lookupReportsNoTeamSoTheUiCanFallBackToManualEntry() throws Exception {
        Session loner = signupAndLogin("lookup-loner");
        Session other = signupAndLogin("lookup-other");

        mockMvc.perform(get("/api/members/lookup").param("name", other.name())
                        .header("Authorization", "Bearer " + loner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasTeam").value(false))
                .andExpect(jsonPath("$.matches.length()").value(0));
    }

    @Test
    void lookupRejectsNamesTooShortToBeSpecific() throws Exception {
        Session leader = signupAndLogin("short-lookup");
        createTeam(leader, uniqueTeamName("short"));

        mockMvc.perform(get("/api/members/lookup").param("name", "A")
                        .header("Authorization", "Bearer " + leader.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches.length()").value(0));
    }

    // ---------- 카드와 팀 연동 ----------

    private Long uploadCard(Session uploader, String receiverEmail) throws Exception {
        var audio = new org.springframework.mock.web.MockMultipartFile(
                "audio", "note.wav", "audio/wav", "fake-audio".getBytes());
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/handover-cards").file(audio)
                        .param("senderName", uploader.name())
                        .param("receiverName", "받는사람")
                        .param("receiverEmail", receiverEmail == null ? "" : receiverEmail)
                        .param("sourceLanguage", "ko")
                        .param("targetLanguage", "en")
                        .header("Authorization", "Bearer " + uploader.accessToken()))
                .andExpect(status().isAccepted())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void teammatesCanSeeACardCreatedInTheirTeam() throws Exception {
        Session leader = signupAndLogin("card-leader");
        Session teammate = signupAndLogin("card-teammate");
        Session outsider = signupAndLogin("card-outsider");
        Long teamId = createTeam(leader, uniqueTeamName("card-share"));
        joinTeam(teammate, leader, teamId);

        Long cardId = uploadCard(leader, null);

        // 같은 팀이면 소유자도 수신자도 아니지만 볼 수 있다
        mockMvc.perform(get("/api/handover-cards/{id}", cardId)
                        .header("Authorization", "Bearer " + teammate.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamName").isNotEmpty());

        // 팀 밖 회원에게는 존재 자체를 숨긴다
        mockMvc.perform(get("/api/handover-cards/{id}", cardId)
                        .header("Authorization", "Bearer " + outsider.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void teamCardsShowUpInATeammatesList() throws Exception {
        Session leader = signupAndLogin("list-card-leader");
        Session teammate = signupAndLogin("list-card-teammate");
        Long teamId = createTeam(leader, uniqueTeamName("card-list"));
        joinTeam(teammate, leader, teamId);

        Long cardId = uploadCard(leader, null);

        MvcResult result = mockMvc.perform(get("/api/handover-cards")
                        .header("Authorization", "Bearer " + teammate.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        boolean found = false;
        for (JsonNode card : body.get("content")) {
            if (card.get("id").asLong() == cardId) {
                found = true;
            }
        }
        org.assertj.core.api.Assertions.assertThat(found).isTrue();
    }

    @Test
    void cardsMadeBeforeTheUploaderHadATeamStayOutOfTheTeam() throws Exception {
        Session uploader = signupAndLogin("pre-team-uploader");
        Session teammate = signupAndLogin("pre-team-mate");

        // 아직 팀이 없을 때 만든 카드
        Long cardBeforeTeam = uploadCard(uploader, null);

        Long teamId = createTeam(uploader, uniqueTeamName("pre-team"));
        joinTeam(teammate, uploader, teamId);
        Long cardAfterTeam = uploadCard(uploader, null);

        // 팀이 생기기 전에 만든 카드는 팀에 묶이지 않았으므로 팀원에게 보이지 않는다
        mockMvc.perform(get("/api/handover-cards/{id}", cardBeforeTeam)
                        .header("Authorization", "Bearer " + teammate.accessToken()))
                .andExpect(status().isNotFound());

        // 팀이 생긴 뒤에 만든 카드는 공유된다
        mockMvc.perform(get("/api/handover-cards/{id}", cardAfterTeam)
                        .header("Authorization", "Bearer " + teammate.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void cardCreatedWithoutATeamStaysPrivate() throws Exception {
        Session loner = signupAndLogin("teamless-uploader");
        Session other = signupAndLogin("teamless-other");

        Long cardId = uploadCard(loner, null);

        // 팀이 없는 회원끼리 카드가 새어나가면 안 된다
        mockMvc.perform(get("/api/handover-cards/{id}", cardId)
                        .header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/handover-cards/{id}", cardId)
                        .header("Authorization", "Bearer " + loner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamName").doesNotExist());
    }

    @Test
    void leavingTheTeamRemovesAccessToItsCards() throws Exception {
        Session leader = signupAndLogin("leave-card-leader");
        Session member = signupAndLogin("leave-card-member");
        Long teamId = createTeam(leader, uniqueTeamName("leave-card"));
        joinTeam(member, leader, teamId);
        Long cardId = uploadCard(leader, null);

        mockMvc.perform(get("/api/handover-cards/{id}", cardId)
                        .header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/teams/me/membership").header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/handover-cards/{id}", cardId)
                        .header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingATeamKeepsItsCardsForOwnerAndReceiver() throws Exception {
        Session leader = signupAndLogin("card-team-delete");
        Session teammate = signupAndLogin("card-team-delete-mate");
        Long teamId = createTeam(leader, uniqueTeamName("card-team-delete"));
        joinTeam(teammate, leader, teamId);
        Long cardId = uploadCard(leader, null);

        mockMvc.perform(delete("/api/teams/me").header("Authorization", "Bearer " + leader.accessToken()))
                .andExpect(status().isNoContent());

        // 카드는 남고 소유자는 계속 볼 수 있어야 한다
        mockMvc.perform(get("/api/handover-cards/{id}", cardId)
                        .header("Authorization", "Bearer " + leader.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamName").doesNotExist());

        // 팀이 사라졌으니 옛 팀원은 더 이상 볼 수 없다
        mockMvc.perform(get("/api/handover-cards/{id}", cardId)
                        .header("Authorization", "Bearer " + teammate.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void teamEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/teams")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/teams/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/members/lookup").param("name", "Alex")).andExpect(status().isUnauthorized());
    }
}
