package com.handovercard.web;

import com.handovercard.auth.dto.TokenResponse;
import com.handovercard.security.JwtAuthenticationFilter;
import com.handovercard.security.JwtProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * SSR 화면의 인증 토큰을 쿠키로 주고받는다. 폼 로그인과 소셜 로그인이 똑같은 쿠키를 심어야
 * 이후 요청이 어느 쪽으로 들어왔는지 신경 쓰지 않아도 된다.
 */
@Component
public class AuthTokenCookies {

    public static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    private final JwtProperties jwtProperties;
    private final CookieProperties cookieProperties;

    public AuthTokenCookies(JwtProperties jwtProperties, CookieProperties cookieProperties) {
        this.jwtProperties = jwtProperties;
        this.cookieProperties = cookieProperties;
    }

    public void write(HttpServletResponse response, TokenResponse tokens) {
        add(response, JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE, tokens.accessToken(),
                jwtProperties.accessTokenValiditySeconds());
        add(response, REFRESH_TOKEN_COOKIE, tokens.refreshToken(),
                jwtProperties.refreshTokenValiditySeconds());
    }

    public void clear(HttpServletResponse response) {
        add(response, JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE, "", 0);
        add(response, REFRESH_TOKEN_COOKIE, "", 0);
    }

    private void add(HttpServletResponse response, String name, String value, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                // HTTPS 환경에서 켠다(운영 프로파일 기본값). 켜면 평문 연결로는 토큰이 나가지 않는다.
                .secure(cookieProperties.secure())
                .path("/")
                .maxAge(maxAgeSeconds)
                // CSRF가 꺼져 있는 상태에서 쿠키 인증을 쓰므로 크로스 사이트 전송을 막아 둔다.
                // 소셜 로그인은 공급자에서 우리 쪽으로 돌아오는 최상위 이동(GET)이라 Lax로도 쿠키가 남는다.
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
