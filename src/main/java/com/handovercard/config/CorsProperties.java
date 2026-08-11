package com.handovercard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "handover.web.cors")
public record CorsProperties(

        /**
         * 브라우저에서 API를 직접 호출할 수 있는 출처 목록. 프론트엔드를 별도 주소에 두면
         * 그 주소를 넣는다(개발 서버 포함). 비워 두면 CORS 헤더를 아예 붙이지 않으므로,
         * 화면과 API를 한 서버가 서비스하는 지금 구성에서는 기본값이 빈 목록이다.
         *
         * <p>정확한 주소({@code https://www.example.com})와 패턴({@code http://localhost:[*]})을
         * 모두 받는다. 호스트와 스킴은 그대로 비교하므로 {@code localhost}와 {@code 127.0.0.1},
         * {@code http}와 {@code https}는 각각 따로 넣어야 한다.
         */
        List<String> allowedOrigins
) {
}
