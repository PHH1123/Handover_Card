package com.handovercard.web;

import com.handovercard.auth.AuthService;
import com.handovercard.auth.dto.TokenResponse;
import com.handovercard.auth.oauth2.SocialMemberPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 소셜 로그인이 끝나면 폼 로그인과 똑같은 JWT 액세스/리프레시 쿠키를 심고 카드 목록으로 보낸다.
 *
 * <p>이 지점부터는 소셜 로그인이었다는 사실이 남지 않는다. 인증 방식이 늘어나도 나머지 화면과 API는
 * 그대로 JWT 하나만 다루면 된다.
 */
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthService authService;
    private final AuthTokenCookies authTokenCookies;

    public OAuth2LoginSuccessHandler(AuthService authService, AuthTokenCookies authTokenCookies) {
        this.authService = authService;
        this.authTokenCookies = authTokenCookies;
        setDefaultTargetUrl("/web/cards");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        SocialMemberPrincipal principal = (SocialMemberPrincipal) authentication.getPrincipal();
        TokenResponse tokens = authService.issueTokensFor(principal.getMember());
        authTokenCookies.write(response, tokens);

        // 인가 요청을 잠깐 담아 두느라 만들어진 세션. 이후 인증은 전부 쿠키의 JWT로 하므로 여기서 버린다.
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        getRedirectStrategy().sendRedirect(request, response, getDefaultTargetUrl());
    }
}
