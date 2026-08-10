package com.handovercard.auth.oauth2.dto;

import com.handovercard.member.AuthProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

import java.util.Set;

/**
 * 클라이언트가 인가 요청을 직접 만들 수 있도록 공급자 설정을 알려준다.
 * 클라이언트 시크릿은 포함하지 않는다(여기 담긴 값은 모두 공개되어도 되는 것들이다).
 */
public record SocialProviderResponse(String provider, String displayName, String authorizationUri,
                                      String clientId, Set<String> scopes) {

    public static SocialProviderResponse of(AuthProvider provider, ClientRegistration registration) {
        return new SocialProviderResponse(provider.getRegistrationId(), provider.getDisplayName(),
                registration.getProviderDetails().getAuthorizationUri(), registration.getClientId(),
                registration.getScopes());
    }
}
