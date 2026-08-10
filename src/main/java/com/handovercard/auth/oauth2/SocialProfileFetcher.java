package com.handovercard.auth.oauth2;

import com.handovercard.member.AuthProvider;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;

import java.util.Map;

/**
 * 공급자별 사용자 정보 응답을 {@link OAuth2UserProfile}로 옮긴다.
 *
 * <p>공급자를 늘릴 때 건드릴 곳이 이 구현체 하나가 되도록, 인증 코드 교환·회원 연동 같은 나머지 흐름은
 * 전부 공급자를 모르는 상태로 둔다.
 */
public interface SocialProfileFetcher {

    AuthProvider provider();

    /**
     * @param userRequest 액세스 토큰이 들어 있다. 사용자 정보 응답만으로 부족해 공급자 API를 한 번 더
     *                    호출해야 하는 경우(GitHub 비공개 이메일)에 쓴다.
     * @param attributes  사용자 정보 엔드포인트가 돌려준 원본 속성
     */
    OAuth2UserProfile fetch(OAuth2UserRequest userRequest, Map<String, Object> attributes);
}
