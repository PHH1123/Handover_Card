package com.handovercard.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponse(
        @Schema(description = "인증이 필요한 API 호출 시 Authorization 헤더에 담을 액세스 토큰")
        String accessToken,

        @Schema(description = "액세스 토큰 재발급에 사용하는 리프레시 토큰")
        String refreshToken,

        @Schema(description = "토큰 타입", example = "Bearer")
        String tokenType,

        @Schema(description = "액세스 토큰 만료까지 남은 시간(초)")
        long expiresInSeconds
) {
}
