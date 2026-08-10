package com.handovercard.web;

import com.handovercard.auth.oauth2.SocialMemberOAuth2UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 소셜 로그인 실패를 로그인 화면으로 되돌린다.
 *
 * <p>실패 사유는 코드로만 넘긴다. 공급자가 준 문장을 쿼리 파라미터로 실어 그대로 보여주면 로그인 화면에
 * 아무 문구나 띄우는 통로가 되기 때문이다. 자세한 내용은 서버 로그에만 남긴다.
 */
@Component
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        String errorCode = exception instanceof OAuth2AuthenticationException oauth2Exception
                ? oauth2Exception.getError().getErrorCode()
                : SocialMemberOAuth2UserService.SOCIAL_LOGIN_REJECTED;
        log.warn("Social login failed ({}): {}", errorCode, exception.getMessage());

        String reason = SocialMemberOAuth2UserService.UNVERIFIED_EMAIL.equals(errorCode)
                ? SocialMemberOAuth2UserService.UNVERIFIED_EMAIL
                : SocialMemberOAuth2UserService.SOCIAL_LOGIN_REJECTED;
        getRedirectStrategy().sendRedirect(request, response, "/web/login?error=" + reason);
    }
}
