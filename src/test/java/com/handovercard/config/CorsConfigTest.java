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
@TestPropertySource(properties = "handover.web.cors.allowed-origins=http://localhost:5173")
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
