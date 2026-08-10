package com.handovercard.auth.oauth2;

import com.handovercard.member.AuthProvider;
import com.handovercard.member.Member;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.stereotype.Service;

/**
 * API 클라이언트(모바일·SPA)가 직접 받아 온 인가 코드를 회원으로 바꾼다.
 *
 * <p>브라우저 흐름과 달리 사용자를 공급자로 보내는 일은 클라이언트가 하고, 서버는 코드를 토큰으로
 * 바꾸는 단계부터 맡는다. 클라이언트 시크릿이 서버 밖으로 나가지 않는다는 점은 두 흐름이 같다.
 *
 * <p><b>state 검증은 클라이언트 몫이다.</b> 인가 요청을 만든 쪽만 state를 알고 있으므로, 코드를 여기로
 * 보내기 전에 클라이언트가 돌려받은 state를 자기가 보낸 값과 비교해야 한다. 서버는 코드가 어느 요청에서
 * 왔는지 알 수 없어 대신 검사해 줄 수 없다.
 */
@Service
public class OAuth2CodeExchangeService {

    /** 인가 요청과 응답을 짝지어 주기만 하는 값. 실제 검증은 요청을 만든 클라이언트가 한다. */
    private static final String UNUSED_STATE = "api-client-managed";

    private final SocialLoginProviders socialLoginProviders;
    private final SocialMemberOAuth2UserService userService;
    private final OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> tokenResponseClient;

    public OAuth2CodeExchangeService(SocialLoginProviders socialLoginProviders,
                                      SocialMemberOAuth2UserService userService) {
        this.socialLoginProviders = socialLoginProviders;
        this.userService = userService;
        this.tokenResponseClient = new RestClientAuthorizationCodeTokenResponseClient();
    }

    public Member exchange(AuthProvider provider, String code, String redirectUri) {
        ClientRegistration registration = socialLoginProviders.require(provider);

        OAuth2AccessTokenResponse tokenResponse;
        try {
            tokenResponse = tokenResponseClient.getTokenResponse(
                    new OAuth2AuthorizationCodeGrantRequest(registration, exchangeOf(registration, code, redirectUri)));
        } catch (OAuth2AuthorizationException e) {
            // 코드가 이미 쓰였거나 만료됐거나 redirectUri가 발급 때와 다른 경우가 대부분이다.
            throw new SocialLoginException(
                    provider.getDisplayName() + " 인가 코드를 토큰으로 교환하지 못했습니다: " + e.getError().getErrorCode(), e);
        }

        OAuth2UserRequest userRequest = new OAuth2UserRequest(registration, tokenResponse.getAccessToken(),
                tokenResponse.getAdditionalParameters());
        return ((SocialMemberPrincipal) userService.loadUser(userRequest)).getMember();
    }

    private OAuth2AuthorizationExchange exchangeOf(ClientRegistration registration, String code, String redirectUri) {
        OAuth2AuthorizationRequest request = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(registration.getProviderDetails().getAuthorizationUri())
                .clientId(registration.getClientId())
                .redirectUri(redirectUri)
                .scopes(registration.getScopes())
                .state(UNUSED_STATE)
                .build();
        OAuth2AuthorizationResponse response = OAuth2AuthorizationResponse.success(code)
                .redirectUri(redirectUri)
                .state(UNUSED_STATE)
                .build();
        return new OAuth2AuthorizationExchange(request, response);
    }
}
