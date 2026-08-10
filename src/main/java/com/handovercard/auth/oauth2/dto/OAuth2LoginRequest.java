package com.handovercard.auth.oauth2.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * @param code        공급자가 클라이언트에 돌려준 일회용 인가 코드
 * @param redirectUri 그 코드를 받을 때 쓴 리다이렉트 URI. 공급자가 발급 때와 같은 값인지 확인하므로
 *                    클라이언트가 실제로 사용한 값을 그대로 보내야 한다.
 */
public record OAuth2LoginRequest(
        @Schema(description = "공급자에게 받은 인가 코드", example = "4/0Ab_5qlk...")
        @NotBlank String code,

        @Schema(description = "인가 요청에 사용한 리다이렉트 URI", example = "https://app.example.com/oauth2/callback")
        @NotBlank String redirectUri) {
}
