package com.handovercard.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(description = "가입 시 사용한 이메일", example = "user@example.com")
        @NotBlank @Email String email,

        @Schema(description = "비밀번호", example = "password123")
        @NotBlank String password
) {
}
