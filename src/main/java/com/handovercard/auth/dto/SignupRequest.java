package com.handovercard.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Schema(description = "로그인에 사용할 이메일", example = "user@example.com")
        @NotBlank @Email String email,

        @Schema(description = "비밀번호 (8~100자)", example = "password123")
        @NotBlank @Size(min = 8, max = 100) String password,

        @Schema(description = "표시용 이름", example = "홍길동")
        @NotBlank String name
) {
}
