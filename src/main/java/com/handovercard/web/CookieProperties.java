package com.handovercard.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "handover.web.cookie")
public record CookieProperties(

        /**
         * 인증 쿠키에 Secure 속성을 붙일지 여부. 붙이면 브라우저가 평문(HTTP) 연결로는 쿠키를
         * 보내지 않는다. HTTPS로 서비스하는 환경에서는 반드시 켜야 하고, 로컬 개발은 HTTP라
         * 켜면 로그인이 되지 않으므로 기본값은 꺼짐이다.
         */
        boolean secure
) {
}
