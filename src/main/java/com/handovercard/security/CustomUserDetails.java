package com.handovercard.security;

import com.handovercard.member.Member;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

public class CustomUserDetails implements UserDetails {

    private final Member member;

    public CustomUserDetails(Member member) {
        this.member = member;
    }

    public Member getMember() {
        return member;
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name()));
    }

    /**
     * 소셜 로그인 전용 회원은 비밀번호가 없다. {@code null}을 그대로 넘기면 인코더가 터지므로
     * 어떤 입력과도 일치하지 않는 빈 해시를 돌려줘 "비밀번호 불일치"로 떨어지게 한다.
     */
    @Override
    public String getPassword() {
        return member.hasPassword() ? member.getPassword() : "";
    }

    @Override
    public String getUsername() {
        return member.getEmail();
    }
}
