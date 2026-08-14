package com.handovercard.web;

import com.handovercard.team.TeamRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * SSR 화면이 실제로 렌더링되는지 확인한다.
 *
 * <p>REST API 테스트만으로는 Thymeleaf 템플릿을 한 번도 실행하지 않기 때문에 화면이 통째로 깨져도
 * 알 수 없다. 실제로 팀장 화면에서 {@code th:onsubmit} 안에 문자열 표현식을 쓴 탓에 페이지 전체가
 * 500으로 죽은 적이 있는데, 그때 API 테스트는 모두 통과하고 있었다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebPageRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TeamRepository teamRepository;

    private record Session(String email, String name, Cookie[] cookies) {
    }

    // ---------- 헬퍼 ----------

    /** 회원가입 후 SSR 로그인까지 마쳐 인증 쿠키를 얻는다. */
    private Session signupAndLogin(String label) throws Exception {
        String email = label + "-" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/web/signup")
                        .param("email", email).param("password", "password123").param("name", label))
                .andExpect(status().is3xxRedirection());

        MvcResult login = mockMvc.perform(post("/web/login")
                        .param("email", email).param("password", "password123"))
                .andExpect(redirectedUrl("/web/cards"))
                .andReturn();
        return new Session(email, label, login.getResponse().getCookies());
    }

    /**
     * 팀을 만들고 그 팀의 ID를 돌려준다.
     * 테스트들이 같은 H2 인스턴스를 공유해 팀이 여러 개 쌓이므로, 화면에서 첫 번째 링크를 줍지 않고
     * 이름으로 정확히 찾는다.
     */
    private Long createTeam(Session session, String name) throws Exception {
        mockMvc.perform(post("/web/teams").cookie(session.cookies()).param("name", name))
                .andExpect(redirectedUrl("/web/teams"));
        return teamRepository.findAllByOrderByNameAsc().stream()
                .filter(team -> team.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("만든 팀을 찾지 못했습니다: " + name))
                .getId();
    }

    private void applyToTeam(Session member, Long teamId) throws Exception {
        mockMvc.perform(post("/web/teams/{id}/join", teamId).cookie(member.cookies()))
                .andExpect(redirectedUrl("/web/teams"));
    }

    private void joinTeam(Session member, Session leader, Long teamId) throws Exception {
        applyToTeam(member, teamId);

        String page = mockMvc.perform(get("/web/teams").cookie(leader.cookies()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // 승인 폼의 action에서 신청 ID를 꺼낸다
        var matcher = java.util.regex.Pattern
                .compile("/web/teams/requests/(\\d+)/approve").matcher(page);
        if (!matcher.find()) {
            throw new IllegalStateException("승인 대기 중인 신청이 화면에 없습니다");
        }
        mockMvc.perform(post("/web/teams/requests/{id}/approve", matcher.group(1)).cookie(leader.cookies()))
                .andExpect(redirectedUrl("/web/teams"));
    }

    /** 리다이렉트 뒤의 화면을 같은 세션으로 이어서 연다. 플래시 메시지는 세션에 실려 오기 때문이다. */
    private String followRedirect(String url, Session session, MvcResult previous) throws Exception {
        MockHttpSession httpSession = (MockHttpSession) previous.getRequest().getSession(false);
        var request = get(url).cookie(session.cookies());
        if (httpSession != null) {
            request = request.session(httpSession);
        }
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Long uploadCard(Session uploader) throws Exception {
        MockMultipartFile audio = new MockMultipartFile("audio", "note.wav", "audio/wav", "fake".getBytes());
        MvcResult result = mockMvc.perform(multipart("/web/cards").file(audio).cookie(uploader.cookies())
                        .param("senderName", uploader.name())
                        .param("receiverName", "받는사람")
                        .param("receiverEmail", "")
                        .param("sourceLanguage", "ko")
                        .param("targetLanguage", "en"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = result.getResponse().getRedirectedUrl();
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }

    // ---------- 인증 화면 ----------

    @Test
    void loginPageRenders() throws Exception {
        mockMvc.perform(get("/web/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(containsString("로그인")))
                .andExpect(content().string(containsString("name=\"password\"")));
    }

    @Test
    void signupPageRenders() throws Exception {
        mockMvc.perform(get("/web/signup"))
                .andExpect(status().isOk())
                .andExpect(view().name("signup"))
                .andExpect(content().string(containsString("회원가입")));
    }

    @Test
    void signupPageRedisplaysFieldErrors() throws Exception {
        mockMvc.perform(post("/web/signup")
                        .param("email", "not-an-email").param("password", "short").param("name", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("signup"))
                .andExpect(content().string(containsString("field-error")));
    }

    @Test
    void badLoginRedisplaysTheFormWithAnError() throws Exception {
        mockMvc.perform(post("/web/login").param("email", "nobody@example.com").param("password", "wrong-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(containsString("올바르지 않습니다")));
    }

    @Test
    void protectedPagesRedirectToLoginWhenSignedOut() throws Exception {
        mockMvc.perform(get("/web/cards")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/web/login"));
        mockMvc.perform(get("/web/teams")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/web/login"));
    }

    @Test
    void rootRedirectsToTheCardList() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/web/cards"));
    }

    // ---------- 카드 화면 ----------

    @Test
    void cardListPageRendersTheUploadForm() throws Exception {
        Session session = signupAndLogin("cards-page");

        mockMvc.perform(get("/web/cards").cookie(session.cookies()))
                .andExpect(status().isOk())
                .andExpect(view().name("cards"))
                // 발신자는 로그인 회원으로 고정되어 읽기 전용으로 나온다
                .andExpect(content().string(containsString("readonly")))
                .andExpect(content().string(containsString(session.name())))
                // 언어 기본값은 한국어 → 영어
                .andExpect(content().string(containsString("<option value=\"ko\" selected=\"selected\">한국어 (ko)</option>")))
                .andExpect(content().string(containsString("<option value=\"en\" selected=\"selected\">영어 (en)</option>")))
                // 파일 업로드 / 직접 녹음 탭
                .andExpect(content().string(containsString("data-tab=\"file\"")))
                .andExpect(content().string(containsString("data-tab=\"record\"")))
                .andExpect(content().string(containsString("MediaRecorder")));
    }

    @Test
    void uploadingWithoutAudioRedisplaysTheFormWithAnError() throws Exception {
        Session session = signupAndLogin("no-audio");

        mockMvc.perform(multipart("/web/cards").cookie(session.cookies())
                        .param("senderName", session.name())
                        .param("receiverName", "받는사람")
                        .param("sourceLanguage", "ko")
                        .param("targetLanguage", "en"))
                .andExpect(status().isOk())
                .andExpect(view().name("cards"))
                .andExpect(content().string(containsString("음성 파일을 선택해 주세요")));
    }

    @Test
    void cardDetailPageRenders() throws Exception {
        Session session = signupAndLogin("card-detail");
        Long cardId = uploadCard(session);

        mockMvc.perform(get("/web/cards/{id}", cardId).cookie(session.cookies()))
                .andExpect(status().isOk())
                .andExpect(view().name("card-detail"))
                .andExpect(content().string(containsString("카드 #" + cardId)))
                .andExpect(content().string(containsString("전사")))
                .andExpect(content().string(containsString("요약")))
                // 팀 없이 만든 카드는 공유되지 않는다고 표시된다
                .andExpect(content().string(containsString("본인과 수신자만")));
    }

    @Test
    void cardListShowsAnUploadedCard() throws Exception {
        Session session = signupAndLogin("card-listed");
        Long cardId = uploadCard(session);

        mockMvc.perform(get("/web/cards").cookie(session.cookies()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/web/cards/" + cardId)))
                .andExpect(content().string(not(containsString("아직 카드가 없습니다"))));
    }

    /** 파이프라인이 끝나야 결과 수정 폼이 뜨므로 화면이 COMPLETED가 될 때까지 기다린다. */
    private String awaitCompletedDetailPage(Session session, Long cardId) throws Exception {
        java.time.Instant deadline = java.time.Instant.now().plusSeconds(20);
        String page = "";
        while (java.time.Instant.now().isBefore(deadline)) {
            page = mockMvc.perform(get("/web/cards/{id}", cardId).cookie(session.cookies()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            if (page.contains(">COMPLETED<")) {
                return page;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("카드가 20초 안에 COMPLETED가 되지 않았습니다: " + cardId);
    }

    // ---------- 결과 수정 화면 ----------

    @Test
    void resultEditFormIsShownToTheOwnerOnceProcessingIsDone() throws Exception {
        Session owner = signupAndLogin("edit-form");
        Long cardId = uploadCard(owner);

        String page = awaitCompletedDetailPage(owner, cardId);
        org.assertj.core.api.Assertions.assertThat(page)
                .contains("결과 수정")
                .contains("/web/cards/" + cardId + "/result")
                .contains("name=\"transcript\"")
                .contains("name=\"keyPointTarget\"")
                .contains("name=\"actionItemTarget\"")
                .contains("name=\"blockerTarget\"");
    }

    @Test
    void teammatesSeeTheResultButNotTheEditForm() throws Exception {
        Session leader = signupAndLogin("edit-leader");
        Session teammate = signupAndLogin("edit-teammate");
        Long teamId = createTeam(leader, "edit-team-" + System.nanoTime());
        joinTeam(teammate, leader, teamId);
        Long cardId = uploadCard(leader);
        awaitCompletedDetailPage(leader, cardId);

        mockMvc.perform(get("/web/cards/{id}", cardId).cookie(teammate.cookies()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("요약")))
                // 수정 폼 자체가 없어야 한다 (문구는 스타일시트 주석에도 나오므로 form action으로 본다)
                .andExpect(content().string(not(containsString("/web/cards/" + cardId + "/result"))))
                .andExpect(content().string(not(containsString("name=\"keyPointTarget\""))));
    }

    @Test
    void editingTheResultFromTheFormUpdatesThePage() throws Exception {
        Session owner = signupAndLogin("edit-submit");
        Long cardId = uploadCard(owner);
        awaitCompletedDetailPage(owner, cardId);

        MvcResult saved = mockMvc.perform(post("/web/cards/{id}/result", cardId).cookie(owner.cookies())
                        .param("transcript", "화면에서 고친 전사")
                        .param("translatedText", "edited from the web")
                        // 첫 줄은 고치고, 둘째 줄은 새 항목, 셋째 줄은 빈 칸이라 저장되지 않는다
                        .param("keyPointTarget", "edited key point", "added key point", "")
                        .param("keyPointSource", "고친 핵심", "추가한 핵심", "")
                        .param("actionItemTarget", "")
                        .param("actionItemSource", "")
                        .param("blockerTarget", "")
                        .param("blockerSource", ""))
                .andExpect(redirectedUrl("/web/cards/" + cardId))
                .andReturn();

        String page = followRedirect("/web/cards/" + cardId, owner, saved);
        org.assertj.core.api.Assertions.assertThat(page)
                .contains("결과를 수정했습니다.")
                .contains("화면에서 고친 전사")
                .contains("edited from the web")
                .contains("edited key point")
                .contains("추가한 핵심");
    }

    /**
     * 요약 입력이 하나도 없는 요청은 "요약을 비우라"가 아니다.
     * 우리 폼은 빈 칸이라도 함께 보내지만, 그렇지 않은 요청에 요약이 통째로 날아가면 안 된다.
     */
    @Test
    void aPostWithoutSummaryInputsLeavesTheSummaryAlone() throws Exception {
        Session owner = signupAndLogin("edit-keep-summary");
        Long cardId = uploadCard(owner);
        awaitCompletedDetailPage(owner, cardId);

        mockMvc.perform(post("/web/cards/{id}/result", cardId).cookie(owner.cookies())
                        .param("transcript", "전사만 고침")
                        .param("keyPointTarget", "keep me")
                        .param("keyPointSource", "남겨야 할 핵심"))
                .andExpect(redirectedUrl("/web/cards/" + cardId));

        // 요약 파라미터 없이 전사만 보낸다
        mockMvc.perform(post("/web/cards/{id}/result", cardId).cookie(owner.cookies())
                        .param("transcript", "전사를 또 고침"))
                .andExpect(redirectedUrl("/web/cards/" + cardId));

        mockMvc.perform(get("/web/cards/{id}", cardId).cookie(owner.cookies()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("전사를 또 고침")))
                .andExpect(content().string(containsString("keep me")))
                .andExpect(content().string(containsString("남겨야 할 핵심")));
    }

    /** 반대로 빈 칸이 실려 오면(= 폼에서 항목을 모두 지운 경우) 요약은 실제로 비워져야 한다. */
    @Test
    void clearingEveryEntryInTheFormEmptiesTheSummary() throws Exception {
        Session owner = signupAndLogin("edit-clear-summary");
        Long cardId = uploadCard(owner);
        awaitCompletedDetailPage(owner, cardId);

        mockMvc.perform(post("/web/cards/{id}/result", cardId).cookie(owner.cookies())
                        .param("keyPointTarget", "will be gone")
                        .param("keyPointSource", "사라질 핵심"))
                .andExpect(redirectedUrl("/web/cards/" + cardId));

        mockMvc.perform(post("/web/cards/{id}/result", cardId).cookie(owner.cookies())
                        .param("keyPointTarget", "").param("keyPointSource", "")
                        .param("actionItemTarget", "").param("actionItemSource", "")
                        .param("blockerTarget", "").param("blockerSource", ""))
                .andExpect(redirectedUrl("/web/cards/" + cardId));

        mockMvc.perform(get("/web/cards/{id}", cardId).cookie(owner.cookies()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("will be gone"))))
                .andExpect(content().string(not(containsString("사라질 핵심"))));
    }

    /** 비워 둔 텍스트 칸은 "지우기"가 아니라 "그대로 두기"로 읽는다. */
    @Test
    void submittingTheFormWithEmptyTextKeepsTheExistingText() throws Exception {
        Session owner = signupAndLogin("edit-keep");
        Long cardId = uploadCard(owner);
        awaitCompletedDetailPage(owner, cardId);

        // 먼저 알아볼 수 있는 값으로 고쳐 둔다
        mockMvc.perform(post("/web/cards/{id}/result", cardId).cookie(owner.cookies())
                        .param("transcript", "지워지면 안 되는 전사")
                        .param("translatedText", "must survive"))
                .andExpect(redirectedUrl("/web/cards/" + cardId));

        // 텍스트를 비운 채 저장해도 앞서 넣은 내용이 남아 있어야 한다
        mockMvc.perform(post("/web/cards/{id}/result", cardId).cookie(owner.cookies())
                        .param("transcript", "")
                        .param("translatedText", "")
                        .param("keyPointTarget", "")
                        .param("keyPointSource", ""))
                .andExpect(redirectedUrl("/web/cards/" + cardId));

        mockMvc.perform(get("/web/cards/{id}", cardId).cookie(owner.cookies()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("지워지면 안 되는 전사")))
                .andExpect(content().string(containsString("must survive")));
    }

    // ---------- 팀 화면 ----------

    @Test
    void teamPageRendersForAMemberWithoutATeam() throws Exception {
        Session session = signupAndLogin("no-team");

        mockMvc.perform(get("/web/teams").cookie(session.cookies()))
                .andExpect(status().isOk())
                .andExpect(view().name("teams"))
                .andExpect(content().string(containsString("팀 만들기")))
                .andExpect(content().string(containsString("팀 가입 신청")));
    }

    @Test
    void teamPageRendersForALeaderWhoseTeamIsEmpty() throws Exception {
        Session leader = signupAndLogin("solo-leader");
        createTeam(leader, "solo-team-" + System.nanoTime());

        mockMvc.perform(get("/web/teams").cookie(leader.cookies()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("내가 팀장입니다")))
                .andExpect(content().string(containsString("팀 삭제")))
                // 팀원이 없으면 위임·추방 버튼은 나오지 않는다
                .andExpect(content().string(not(containsString("팀장 위임"))));
    }

    /**
     * 팀장 + 팀원이 있는 화면. 위임/추방 버튼이 렌더링되는 경로로,
     * 실제로 여기서 Thymeleaf 표현식 제약 때문에 페이지 전체가 500으로 죽은 적이 있다.
     */
    @Test
    void teamPageRendersManageButtonsForALeaderWithMembers() throws Exception {
        Session leader = signupAndLogin("crew-leader");
        Session member = signupAndLogin("crew-member");
        Long teamId = createTeam(leader, "crew-team-" + System.nanoTime());
        joinTeam(member, leader, teamId);

        mockMvc.perform(get("/web/teams").cookie(leader.cookies()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("팀장 위임")))
                .andExpect(content().string(containsString("추방")))
                .andExpect(content().string(containsString("/promote")))
                .andExpect(content().string(containsString("/kick")))
                // 확인 문구는 data 속성으로 넘긴다 (th:on* 안에서는 문자열 표현식이 금지된다)
                .andExpect(content().string(containsString("data-confirm")))
                .andExpect(content().string(containsString(member.name())));
    }

    @Test
    void teamPageShowsPendingRequestsToTheLeaderOnly() throws Exception {
        Session leader = signupAndLogin("pending-leader");
        Session applicant = signupAndLogin("pending-applicant");
        Long teamId = createTeam(leader, "pending-team-" + System.nanoTime());
        applyToTeam(applicant, teamId);

        mockMvc.perform(get("/web/teams").cookie(leader.cookies()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("가입 신청")))
                .andExpect(content().string(containsString(applicant.email())))
                .andExpect(content().string(containsString("/approve")))
                .andExpect(content().string(containsString("/reject")));

        // 신청자 화면에는 대기 안내만 보이고 승인 버튼은 없다
        mockMvc.perform(get("/web/teams").cookie(applicant.cookies()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("승인 대기 중")))
                .andExpect(content().string(not(containsString("/approve"))));
    }

    @Test
    void anApplicantCanCancelTheirRequestFromTheTeamPage() throws Exception {
        Session leader = signupAndLogin("cancel-page-leader");
        Session applicant = signupAndLogin("cancel-page-applicant");
        Long teamId = createTeam(leader, "cancel-page-team-" + System.nanoTime());
        applyToTeam(applicant, teamId);

        String waiting = mockMvc.perform(get("/web/teams").cookie(applicant.cookies()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(waiting).contains("신청 취소").contains("/cancel");

        var matcher = java.util.regex.Pattern.compile("/web/teams/requests/(\\d+)/cancel").matcher(waiting);
        org.assertj.core.api.Assertions.assertThat(matcher.find()).isTrue();
        MvcResult cancelled = mockMvc.perform(post("/web/teams/requests/{id}/cancel", matcher.group(1))
                        .cookie(applicant.cookies()))
                .andExpect(redirectedUrl("/web/teams"))
                .andReturn();

        String page = followRedirect("/web/teams", applicant, cancelled);
        org.assertj.core.api.Assertions.assertThat(page)
                .contains("가입 신청을 취소했습니다")
                .doesNotContain("승인 대기 중")
                // 취소는 기록을 남기지 않으므로 신청 내역도 뜨지 않는다
                .doesNotContain("내 신청 내역");

        // 팀장 화면에서도 사라져야 한다
        mockMvc.perform(get("/web/teams").cookie(leader.cookies()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("대기 중인 신청이 없습니다")));
    }

    @Test
    void plainMemberSeesLeaveButNoManageButtons() throws Exception {
        Session leader = signupAndLogin("plain-leader");
        Session member = signupAndLogin("plain-member");
        Long teamId = createTeam(leader, "plain-team-" + System.nanoTime());
        joinTeam(member, leader, teamId);

        mockMvc.perform(get("/web/teams").cookie(member.cookies()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("팀 나가기")))
                .andExpect(content().string(not(containsString("팀장 위임"))))
                .andExpect(content().string(not(containsString("팀 삭제"))));
    }

    @Test
    void teamErrorsAreShownOnTheTeamPage() throws Exception {
        Session leader = signupAndLogin("err-leader");
        createTeam(leader, "err-team-" + System.nanoTime());

        // 팀장은 팀을 나갈 수 없다 — 예외가 500이 아니라 화면 안내로 나와야 한다.
        // 안내 문구만 찾으면 템플릿에 정적으로 박힌 같은 문장에 걸려 항상 통과하므로 alert 요소를 본다.
        MvcResult leave = mockMvc.perform(post("/web/teams/leave").cookie(leader.cookies()))
                .andExpect(redirectedUrl("/web/teams"))
                .andReturn();

        String page = followRedirect("/web/teams", leader, leave);
        org.assertj.core.api.Assertions.assertThat(page)
                .contains("<div class=\"alert error\">팀장은 팀을 나갈 수 없습니다.</div>");
    }

    @Test
    void cardPageShowsTheTeamACardIsSharedWith() throws Exception {
        Session leader = signupAndLogin("share-leader");
        String teamName = "share-team-" + System.nanoTime();
        createTeam(leader, teamName);
        Long cardId = uploadCard(leader);

        mockMvc.perform(get("/web/cards/{id}", cardId).cookie(leader.cookies()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("공유 범위")))
                .andExpect(content().string(containsString(teamName)));
    }

    @Test
    void missingCardRedirectsWithAnErrorInsteadOfRenderingJson() throws Exception {
        Session session = signupAndLogin("missing-card");

        MvcResult missing = mockMvc.perform(get("/web/cards/{id}", 999_999L).cookie(session.cookies()))
                .andExpect(redirectedUrl("/web/cards"))
                .andReturn();

        String page = followRedirect("/web/cards", session, missing);
        org.assertj.core.api.Assertions.assertThat(page).contains("alert error");
    }
}
