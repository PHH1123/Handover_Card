package com.handovercard.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @Schema(description = "로그인/재발급 응답으로 받은 리프레시 토큰")
        @NotBlank String refreshToken
) {
}
