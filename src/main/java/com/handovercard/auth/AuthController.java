package com.handovercard.auth;

import com.handovercard.auth.dto.LoginRequest;
import com.handovercard.auth.dto.MemberResponse;
import com.handovercard.auth.dto.RefreshRequest;
import com.handovercard.auth.dto.SignupRequest;
import com.handovercard.auth.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "회원가입, 로그인, 토큰 재발급/로그아웃")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "회원가입", description = "이메일/비밀번호/이름으로 새 계정을 생성합니다.")
    @SecurityRequirement(name = "")
    @PostMapping("/signup")
    public ResponseEntity<MemberResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @Operation(summary = "로그인", description = "이메일/비밀번호로 로그인하고 액세스/리프레시 토큰을 발급받습니다.")
    @SecurityRequirement(name = "")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "토큰 재발급", description = "유효한 리프레시 토큰으로 액세스/리프레시 토큰을 새로 발급받습니다. 기존 리프레시 토큰은 즉시 폐기됩니다.")
    @SecurityRequirement(name = "")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @Operation(summary = "로그아웃", description = "전달한 리프레시 토큰을 폐기합니다.")
    @SecurityRequirement(name = "")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}
