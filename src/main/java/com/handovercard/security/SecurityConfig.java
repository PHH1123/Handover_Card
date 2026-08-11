package com.handovercard.security;

import com.handovercard.auth.oauth2.SocialMemberOAuth2UserService;
import com.handovercard.web.OAuth2LoginFailureHandler;
import com.handovercard.web.OAuth2LoginSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                           JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                           CustomUserDetailsService userDetailsService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 소셜 로그인(OAuth 2.0) 왕복 전용 체인.
     *
     * <p>다른 체인과 달리 세션을 허용한다. 공급자로 보낸 인가 요청(state·PKCE)을 돌아올 때까지 어딘가
     * 두어야 하고, Spring Security의 기본 저장소가 세션이기 때문이다. 이 세션은 로그인 성공 핸들러가
     * JWT 쿠키를 심으면서 곧바로 버리므로 애플리케이션은 여전히 무상태로 돌아간다.
     *
     * <p>핸들러들을 생성자가 아니라 이 메서드의 인자로 받는 이유는, 성공 핸들러가 {@code AuthService}를
     * 거쳐 이 클래스가 만드는 {@code AuthenticationManager}에 닿아 순환 의존이 되기 때문이다.
     */
    @Bean
    @Order(0)
    public SecurityFilterChain oauth2LoginFilterChain(HttpSecurity http,
                                                       SocialMemberOAuth2UserService socialMemberOAuth2UserService,
                                                       OAuth2LoginSuccessHandler successHandler,
                                                       OAuth2LoginFailureHandler failureHandler) throws Exception {
        http
                .securityMatcher("/oauth2/**", "/login/oauth2/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(socialMemberOAuth2UserService))
                        .successHandler(successHandler)
                        .failureHandler(failureHandler));
        return http.build();
    }

    /**
     * SSR 확인용 화면(`/web/**`) 전용 체인. API와 달리 인증 실패 시 JSON 401 대신 로그인 화면으로 보낸다.
     * 토큰은 REST와 같은 JWT를 쓰되 브라우저가 헤더를 못 붙이므로 쿠키로 전달된다.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/", "/web/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/web/login")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/web/login", "/web/signup").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // API 명세는 공개해 두기로 한 선택이다. 인증 없이는 데이터에 닿지 못하고,
                        // 문서를 열어 두는 편의가 명세가 알려지는 것보다 크다고 봤다.
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // 404는 /error로 넘어가는데 그 경로가 인증 대상이면 401로 바뀐다. 그래서
                        // 공개 경로를 오타로 친 /swagger-ui 같은 요청이 "주소가 틀렸다"가 아니라
                        // "권한이 없다"로 보여 엉뚱한 곳을 뒤지게 만든다. 위에서 열어 둔 접두사
                        // 아래에서만 효과가 있고, 아무 데도 걸리지 않는 경로는 그대로 401이다
                        // (인증 없이 경로 존재 여부를 알려 줄 이유가 없으니 그게 맞다).
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
