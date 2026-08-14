package com.handovercard.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "handover.web.cors.allowed-origins=http://localhost:[*],https://handover-card.vercel.app")
class CorsConfigTest {

    private static final String ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    /**
     * 사전 요청은 인증 헤더 없이 오므로, CORS를 시큐리티 체인에 붙이지 않으면 401로 막힌다.
     * 허용 출처를 설정해 두고도 프론트가 붙지 못하는 가장 흔한 형태라 따로 확인한다.
     */
    @Test
    void 허용된_출처의_사전요청은_인증_없이_통과한다() throws Exception {
        mockMvc.perform(options("/api/handover-cards")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    /** 개발 서버 포트는 프레임워크와 실행 상황에 따라 달라지므로 포트를 가리지 않아야 한다. */
    @Test
    void 패턴은_localhost의_다른_포트도_받는다() throws Exception {
        for (String origin : new String[]{"http://localhost:3000", "http://localhost:5174"}) {
            mockMvc.perform(options("/api/handover-cards")
                            .header(HttpHeaders.ORIGIN, origin)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
        }
    }

    /**
     * 포트만 열어 준 것이지 호스트나 스킴까지 느슨해진 것이 아니다. 패턴을 넣고 나서
     * 실제로 무엇이 열렸는지 오해하기 쉬운 지점이라 경계를 고정해 둔다.
     */
    @Test
    void 패턴은_호스트와_스킴까지_열어_주지는_않는다() throws Exception {
        for (String origin : new String[]{"http://127.0.0.1:5173", "https://localhost:5173"}) {
            mockMvc.perform(options("/api/handover-cards")
                            .header(HttpHeaders.ORIGIN, origin)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isForbidden());
        }
    }

    /** 배포된 프론트 주소(현재 Vercel). 가비아로 옮길 때 이 값도 함께 바꾼다. */
    @Test
    void 정확한_주소도_그대로_동작한다() throws Exception {
        String origin = "https://handover-card.vercel.app";
        mockMvc.perform(options("/api/handover-cards")
                        .header(HttpHeaders.ORIGIN, origin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
    }

    /**
     * vercel.app은 누구나 프로젝트를 만들 수 있는 공용 도메인이다. 정확한 주소로 넣은 것이
     * 이름이 비슷한 다른 프로젝트나 미리보기 주소까지 열어 주지 않는지 경계를 고정한다.
     */
    @Test
    void 이름이_비슷한_다른_vercel_주소는_거부된다() throws Exception {
        String[] origins = {
                "https://handover-card-preview.vercel.app",   // 브랜치 미리보기
                "https://handover-card.vercel.app.evil.com",  // 접미사만 흉내 낸 주소
                "http://handover-card.vercel.app"             // 스킴이 다르다
        };
        for (String origin : origins) {
            mockMvc.perform(options("/api/handover-cards")
                            .header(HttpHeaders.ORIGIN, origin)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void 허용되지_않은_출처는_거부된다() throws Exception {
        mockMvc.perform(options("/api/handover-cards")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    /** SSR 화면은 API와 같은 출처에서 서비스되므로 CORS 규칙을 걸지 않는다. */
    @Test
    void 웹_화면_경로에는_CORS_헤더가_붙지_않는다() throws Exception {
        mockMvc.perform(options("/web/login")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
