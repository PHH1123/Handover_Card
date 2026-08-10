package com.handovercard.auth.oauth2;

import com.handovercard.member.AuthProvider;
import com.handovercard.member.Member;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 공급자의 사용자 정보 엔드포인트를 호출한 뒤 우리 회원까지 연결해서 돌려준다.
 *
 * <p>웹 리다이렉트 로그인은 Spring Security가, REST 로그인은 {@link OAuth2CodeExchangeService}가
 * 이 서비스를 호출한다. "액세스 토큰 → 회원"은 한 곳에만 있다.
 */
@Service
public class SocialMemberOAuth2UserService extends DefaultOAuth2UserService {

    /** 이메일을 확인할 수 없어 막았다. 사용자가 공급자 쪽에서 고칠 수 있는 유일한 실패라 따로 구분한다. */
    public static final String UNVERIFIED_EMAIL = "unverified_email";

    /** 그 밖에 우리 규칙 때문에 로그인을 막은 경우. */
    public static final String SOCIAL_LOGIN_REJECTED = "social_login_rejected";

    private final Map<AuthProvider, SocialProfileFetcher> fetchers;
    private final SocialAccountService socialAccountService;

    public SocialMemberOAuth2UserService(List<SocialProfileFetcher> fetchers,
                                          SocialAccountService socialAccountService) {
        this.fetchers = fetchers.stream()
                .collect(Collectors.toMap(SocialProfileFetcher::provider, Function.identity()));
        this.socialAccountService = socialAccountService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        SocialProfileFetcher fetcher = AuthProvider.ofRegistrationId(registrationId)
                .map(fetchers::get)
                .orElseThrow(() -> reject(SOCIAL_LOGIN_REJECTED,
                        "지원하지 않는 소셜 로그인 공급자입니다: " + registrationId));
        try {
            OAuth2UserProfile profile = fetcher.fetch(userRequest, oauth2User.getAttributes());
            Member member = socialAccountService.resolve(profile);
            return new SocialMemberPrincipal(member, oauth2User.getAttributes());
        } catch (UnverifiedSocialEmailException e) {
            throw reject(UNVERIFIED_EMAIL, e.getMessage());
        } catch (SocialLoginException e) {
            // 인증 필터가 실패 핸들러로 넘길 수 있는 예외로 바꾼다. 그대로 던지면 500이 된다.
            throw reject(SOCIAL_LOGIN_REJECTED, e.getMessage());
        }
    }

    private OAuth2AuthenticationException reject(String errorCode, String message) {
        return new OAuth2AuthenticationException(new OAuth2Error(errorCode, message, null), message);
    }
}
