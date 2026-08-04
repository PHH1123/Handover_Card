package com.handovercard.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI handoverCardOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Handover Card API")
                        .description("""
                                인수인계 음성 녹음을 업로드하면 STT(음성 인식) → 번역 → 요약을 거쳐
                                인수인계 카드로 만들어주는 API입니다.
                                `/api/auth`에서 발급받은 액세스 토큰을 우측 상단 Authorize 버튼에 입력하면
                                인증이 필요한 API를 바로 호출해볼 수 있습니다.
                                """)
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("로그인/토큰 재발급 응답의 accessToken 값을 입력하세요.")));
    }
}
