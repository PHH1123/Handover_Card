package com.handovercard.auth.oauth2;

import com.handovercard.member.AuthProvider;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Google 사용자 정보(`https://www.googleapis.com/oauth2/v3/userinfo`) 매핑.
 *
 * <p>필요한 값이 응답에 전부 들어 있어 추가 호출이 없다.
 */
@Component
class GoogleProfileFetcher implements SocialProfileFetcher {

    @Override
    public AuthProvider provider() {
        return AuthProvider.GOOGLE;
    }

    @Override
    public OAuth2UserProfile fetch(OAuth2UserRequest userRequest, Map<String, Object> attributes) {
        String providerId = asText(attributes.get("sub"));
        if (providerId == null) {
            throw new SocialLoginException("Google 응답에 사용자 식별자(sub)가 없습니다.");
        }
        String email = asText(attributes.get("email"));
        String name = asText(attributes.get("name"));
        return new OAuth2UserProfile(AuthProvider.GOOGLE, providerId, email,
                name != null ? name : email, isTrue(attributes.get("email_verified")));
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    /** `email_verified`는 불리언으로도 문자열로도 오는 것으로 알려져 있어 둘 다 받아들인다. */
    private boolean isTrue(Object value) {
        return switch (value) {
            case Boolean bool -> bool;
            case String text -> Boolean.parseBoolean(text);
            case null, default -> false;
        };
    }
}
