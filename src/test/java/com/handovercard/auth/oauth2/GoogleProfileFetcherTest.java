package com.handovercard.auth.oauth2;

import com.handovercard.member.AuthProvider;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleProfileFetcherTest {

    private final GoogleProfileFetcher fetcher = new GoogleProfileFetcher();

    @Test
    void mapsTheUserInfoResponse() {
        OAuth2UserProfile profile = fetcher.fetch(null,
                Map.of("sub", "1234567890", "email", "alex@example.com", "email_verified", true, "name", "Alex"));

        assertThat(profile.provider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(profile.providerId()).isEqualTo("1234567890");
        assertThat(profile.email()).isEqualTo("alex@example.com");
        assertThat(profile.name()).isEqualTo("Alex");
        assertThat(profile.emailVerified()).isTrue();
    }

    /** `email_verified`가 문자열로 오는 응답도 있어 둘 다 받아들여야 한다. */
    @Test
    void readsEmailVerifiedWhetherItIsABooleanOrAString() {
        assertThat(fetcher.fetch(null, attributes("email_verified", "true")).emailVerified()).isTrue();
        assertThat(fetcher.fetch(null, attributes("email_verified", "false")).emailVerified()).isFalse();
    }

    @Test
    void missingEmailVerifiedMeansNotVerified() {
        Map<String, Object> attributes = attributes("name", "Alex");
        attributes.remove("email_verified");

        assertThat(fetcher.fetch(null, attributes).emailVerified()).isFalse();
    }

    @Test
    void responseWithoutSubjectIsRejected() {
        assertThatThrownBy(() -> fetcher.fetch(null, Map.of("email", "alex@example.com")))
                .isInstanceOf(SocialLoginException.class);
    }

    private Map<String, Object> attributes(String key, Object value) {
        Map<String, Object> attributes = new HashMap<>(
                Map.of("sub", "1234567890", "email", "alex@example.com", "email_verified", true));
        attributes.put(key, value);
        return attributes;
    }
}
