package com.handovercard.auth.oauth2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * GitHub은 `/user` 응답만으로는 이메일을 믿을 수 없다. 주 이메일이면서 확인된 주소를 고르는지,
 * 목록을 못 읽으면 "확인되지 않음"으로 떨어지는지를 확인한다.
 */
class GitHubProfileFetcherTest {

    private static final String API_BASE_URL = "https://api.github.com";

    private MockRestServiceServer server;
    private GitHubProfileFetcher fetcher;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        fetcher = new GitHubProfileFetcher(builder, API_BASE_URL);
    }

    private OAuth2UserRequest userRequest() {
        OAuth2UserRequest request = mock(OAuth2UserRequest.class);
        when(request.getAccessToken()).thenReturn(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "gho_token", Instant.now(), Instant.now().plusSeconds(3600)));
        return request;
    }

    private Map<String, Object> userAttributes() {
        return Map.of("id", 42, "login", "octocat", "name", "The Octocat");
    }

    private void respondWith(String body) {
        server.expect(requestTo(API_BASE_URL + "/user/emails"))
                .andExpect(header("Authorization", "Bearer gho_token"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void usesThePrimaryVerifiedEmail() {
        respondWith("""
                [
                  {"email": "backup@example.com", "primary": false, "verified": true},
                  {"email": "octocat@example.com", "primary": true, "verified": true}
                ]
                """);

        OAuth2UserProfile profile = fetcher.fetch(userRequest(), userAttributes());

        assertThat(profile.providerId()).isEqualTo("42");
        assertThat(profile.email()).isEqualTo("octocat@example.com");
        assertThat(profile.name()).isEqualTo("The Octocat");
        assertThat(profile.emailVerified()).isTrue();
        server.verify();
    }

    @Test
    void unverifiedPrimaryEmailIsNotAccepted() {
        respondWith("""
                [{"email": "octocat@example.com", "primary": true, "verified": false}]
                """);

        OAuth2UserProfile profile = fetcher.fetch(userRequest(), userAttributes());

        assertThat(profile.email()).isNull();
        assertThat(profile.emailVerified()).isFalse();
    }

    @Test
    void missingEmailScopeLeavesTheProfileUnverified() {
        server.expect(requestTo(API_BASE_URL + "/user/emails"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        OAuth2UserProfile profile = fetcher.fetch(userRequest(), userAttributes());

        assertThat(profile.email()).isNull();
        assertThat(profile.emailVerified()).isFalse();
    }

    @Test
    void fallsBackToTheLoginWhenTheAccountHidesItsName() {
        respondWith("""
                [{"email": "octocat@example.com", "primary": true, "verified": true}]
                """);

        OAuth2UserProfile profile = fetcher.fetch(userRequest(), Map.of("id", 42, "login", "octocat"));

        assertThat(profile.name()).isEqualTo("octocat");
    }
}
