package com.handovercard.auth.oauth2;

import com.handovercard.auth.dto.LoginRequest;
import com.handovercard.auth.oauth2.dto.OAuth2LoginRequest;
import com.handovercard.member.Member;
import com.handovercard.member.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 소셜 로그인 배선이 실제로 연결돼 있는지 확인한다. 공급자에 붙는 구간은 테스트할 수 없으므로,
 * 우리 쪽 경계(인가 요청 리다이렉트, 설정되지 않은 공급자 거절, 실패 처리)를 본다.
 *
 * <p>Google만 설정된 것처럼 띄워서, 설정된 공급자와 설정되지 않은 공급자가 어떻게 갈리는지도 함께 본다.
 * 토큰 엔드포인트는 닫힌 포트로 돌려 두어 테스트가 절대 바깥으로 나가지 않는다.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-google-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-secret",
        "spring.security.oauth2.client.provider.google.token-uri=http://127.0.0.1:1/token"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SocialLoginIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void providersEndpointListsOnlyTheConfiguredProviders() throws Exception {
        mockMvc.perform(get("/api/auth/oauth2/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].provider").value("google"))
                .andExpect(jsonPath("$[0].clientId").value("test-google-client-id"))
                .andExpect(jsonPath("$[0].authorizationUri").value(containsString("accounts.google.com")));
    }

    @Test
    void loginPageShowsAButtonOnlyForTheConfiguredProvider() throws Exception {
        mockMvc.perform(get("/web/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/oauth2/authorization/google")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("/oauth2/authorization/github"))));
    }

    @Test
    void authorizationRequestRedirectsToTheProvider() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).startsWith("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(location).contains("client_id=test-google-client-id");
        // openid를 뺀 일반 OAuth2 흐름이어야 GitHub과 같은 코드로 사용자 정보를 읽는다
        assertThat(location).doesNotContain("openid");
    }

    @Test
    void loginPageExplainsAFailedSocialLogin() throws Exception {
        mockMvc.perform(get("/web/login").param("error", SocialMemberOAuth2UserService.UNVERIFIED_EMAIL))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이메일을 확인할 수 없습니다")));
    }

    @Test
    void unconfiguredProviderIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/oauth2/github")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OAuth2LoginRequest("code", "https://app.example.com/callback"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownProviderIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/oauth2/kakao")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OAuth2LoginRequest("code", "https://app.example.com/callback"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authorizationCodeThatCannotBeExchangedIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/oauth2/google")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OAuth2LoginRequest("bad-code", "https://app.example.com/callback"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankCodeIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/oauth2/google")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OAuth2LoginRequest("", "https://app.example.com/callback"))))
                .andExpect(status().isBadRequest());
    }

    /** 소셜 전용 회원은 비밀번호가 없다. 빈 비밀번호로 그 계정에 들어올 수 있으면 안 된다. */
    @Test
    void socialOnlyMemberCannotSignInWithAPassword() throws Exception {
        String email = "social-only-" + System.nanoTime() + "@example.com";
        memberRepository.save(Member.socialOnly(email, "Alex"));

        for (String password : new String[]{"", "password123"}) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                    .andExpect(status().is4xxClientError());
        }
    }
}
