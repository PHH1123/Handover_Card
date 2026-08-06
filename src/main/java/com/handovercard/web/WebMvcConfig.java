package com.handovercard.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 루트로 들어오면 확인용 화면으로 보낸다 (미로그인 시 다시 /web/login으로 이어짐)
        registry.addRedirectViewController("/", "/web/cards");
    }
}
