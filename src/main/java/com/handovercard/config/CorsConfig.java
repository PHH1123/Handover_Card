package com.handovercard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * 다른 주소에서 도는 프론트엔드가 API를 직접 호출할 수 있게 한다.
 *
 * <p>SSR 화면(`/web/**`)은 API와 같은 출처에서 서비스되므로 CORS와 무관하다. 그래서 규칙을
 * `/api/**`에만 건다.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
        // 허용 출처가 없으면 어떤 요청에도 CORS 헤더를 붙이지 않는다. 화면과 API를 한 서버가
        // 서비스하는 동안에는 이 기능이 필요 없고, 켜 두면 열어 줄 이유가 없는 문을 여는 셈이다.
        if (props.allowedOrigins() == null || props.allowedOrigins().isEmpty()) {
            return request -> null;
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // 쿠키로도 인증할 수 있게 열어 둔다. 이 값이 켜져 있으면 출처에 "*"를 쓸 수 없어서
        // 목록을 명시적으로 받는다 -- 프론트를 배포할 때마다 주소를 추가해야 한다는 뜻이다.
        config.setAllowCredentials(true);
        // 사전 요청(preflight) 결과를 재사용해 매 호출마다 왕복이 늘어나지 않게 한다.
        config.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
