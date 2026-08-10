package com.handovercard.auth.oauth2;

import com.handovercard.member.Member;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;

/**
 * OAuth2 로그인이 끝난 시점의 인증 주체. 성공 핸들러가 여기서 회원을 꺼내 우리 JWT를 발급한다.
 *
 * <p>세션에 남는 주체는 이 객체지만, 이후 요청은 전부 JWT 쿠키로 인증되므로 실제로 쓰이는 구간은
 * 로그인 성공 직후 한 번뿐이다.
 */
public class SocialMemberPrincipal implements OAuth2User {

    private final Member member;
    private final Map<String, Object> attributes;

    public SocialMemberPrincipal(Member member, Map<String, Object> attributes) {
        this.member = member;
        this.attributes = attributes;
    }

    public Member getMember() {
        return member;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name()));
    }

    @Override
    public String getName() {
        return member.getEmail();
    }
}
