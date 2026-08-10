package com.handovercard.auth.oauth2;

import com.handovercard.member.AuthProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * GitHub 사용자 정보(`/user`) 매핑.
 *
 * <p>GitHub는 이메일을 비공개로 둘 수 있어 `/user` 응답의 `email`이 자주 {@code null}이고, 값이 있어도
 * 확인 여부를 알려주지 않는다. 그래서 `user:email` 스코프로 `/user/emails`를 한 번 더 호출해
 * <b>주 이메일이면서 확인된</b> 주소를 찾는다. Google과 달리 이 호출 없이는 이메일을 신뢰할 수 없다.
 */
@Component
class GitHubProfileFetcher implements SocialProfileFetcher {

    private static final Logger log = LoggerFactory.getLogger(GitHubProfileFetcher.class);

    private final RestClient restClient;

    GitHubProfileFetcher(RestClient.Builder restClientBuilder,
                          @Value("${handover.oauth2.github.api-base-url:https://api.github.com}") String apiBaseUrl) {
        this.restClient = restClientBuilder.baseUrl(apiBaseUrl).build();
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.GITHUB;
    }

    @Override
    public OAuth2UserProfile fetch(OAuth2UserRequest userRequest, Map<String, Object> attributes) {
        String providerId = asText(attributes.get("id"));
        if (providerId == null) {
            throw new SocialLoginException("GitHub 응답에 사용자 식별자(id)가 없습니다.");
        }
        String login = asText(attributes.get("login"));
        String name = asText(attributes.get("name"));

        VerifiedEmail email = primaryVerifiedEmail(userRequest.getAccessToken().getTokenValue());
        return new OAuth2UserProfile(AuthProvider.GITHUB, providerId, email.address(),
                name != null ? name : login, email.verified());
    }

    private VerifiedEmail primaryVerifiedEmail(String accessToken) {
        List<Map<String, Object>> emails;
        try {
            emails = restClient.get()
                    .uri("/user/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.valueOf("application/vnd.github+json"))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientException e) {
            // 스코프가 빠졌거나 GitHub이 응답하지 않는 경우. 확인되지 않은 이메일로 취급해 연동을 막는다.
            log.warn("Could not read GitHub email list: {}", e.getMessage());
            return new VerifiedEmail(null, false);
        }
        if (emails == null) {
            return new VerifiedEmail(null, false);
        }
        return emails.stream()
                .filter(entry -> isTrue(entry.get("primary")) && isTrue(entry.get("verified")))
                .map(entry -> asText(entry.get("email")))
                .filter(Objects::nonNull)
                .findFirst()
                .map(address -> new VerifiedEmail(address, true))
                .orElseGet(() -> new VerifiedEmail(null, false));
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private boolean isTrue(Object value) {
        return switch (value) {
            case Boolean bool -> bool;
            case String text -> Boolean.parseBoolean(text);
            case null, default -> false;
        };
    }

    private record VerifiedEmail(String address, boolean verified) {
    }
}
