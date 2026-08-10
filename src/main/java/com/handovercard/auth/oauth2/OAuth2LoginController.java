package com.handovercard.auth.oauth2;

import com.handovercard.auth.AuthService;
import com.handovercard.auth.dto.TokenResponse;
import com.handovercard.auth.oauth2.dto.OAuth2LoginRequest;
import com.handovercard.auth.oauth2.dto.SocialProviderResponse;
import com.handovercard.member.AuthProvider;
import com.handovercard.member.Member;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 브라우저 밖(모바일·SPA)에서 소셜 로그인을 끝내는 API.
 *
 * <p>클라이언트가 공급자 화면으로 사용자를 보내 인가 코드를 받고, 그 코드를 여기로 넘기면 서버가
 * 토큰 교환과 회원 연동을 마친 뒤 <b>이메일/비밀번호 로그인과 똑같은 형태의</b> 액세스/리프레시 토큰을
 * 돌려준다. 이후 재발급·로그아웃은 기존 {@code /api/auth/refresh}, {@code /api/auth/logout}을 그대로 쓴다.
 */
@Tag(name = "인증", description = "회원가입, 로그인, 토큰 재발급/로그아웃")
@RestController
@RequestMapping("/api/auth/oauth2")
public class OAuth2LoginController {

    private final OAuth2CodeExchangeService codeExchangeService;
    private final SocialLoginProviders socialLoginProviders;
    private final AuthService authService;

    public OAuth2LoginController(OAuth2CodeExchangeService codeExchangeService,
                                  SocialLoginProviders socialLoginProviders, AuthService authService) {
        this.codeExchangeService = codeExchangeService;
        this.socialLoginProviders = socialLoginProviders;
        this.authService = authService;
    }

    @Operation(summary = "소셜 로그인 공급자 목록",
            description = "이 서버에 설정되어 있는 공급자와, 클라이언트가 인가 요청을 만들 때 필요한 값을 돌려줍니다.")
    @SecurityRequirement(name = "")
    @GetMapping("/providers")
    public ResponseEntity<List<SocialProviderResponse>> providers() {
        List<SocialProviderResponse> providers = socialLoginProviders.configured().stream()
                .map(provider -> SocialProviderResponse.of(provider, socialLoginProviders.require(provider)))
                .toList();
        return ResponseEntity.ok(providers);
    }

    @Operation(summary = "소셜 로그인",
            description = "공급자에게 받은 인가 코드를 액세스/리프레시 토큰으로 교환합니다. "
                    + "처음 로그인하는 소셜 계정이면 같은 이메일의 회원에 연결하고, 없으면 새 회원을 만듭니다. "
                    + "state 검증은 인가 요청을 만든 클라이언트가 해야 합니다.")
    @SecurityRequirement(name = "")
    @PostMapping("/{provider}")
    public ResponseEntity<TokenResponse> login(@PathVariable String provider,
                                                @Valid @RequestBody OAuth2LoginRequest request) {
        AuthProvider authProvider = AuthProvider.ofRegistrationId(provider)
                .orElseThrow(() -> new SocialLoginException("지원하지 않는 소셜 로그인 공급자입니다: " + provider));
        Member member = codeExchangeService.exchange(authProvider, request.code(), request.redirectUri());
        return ResponseEntity.ok(authService.issueTokensFor(member));
    }
}
