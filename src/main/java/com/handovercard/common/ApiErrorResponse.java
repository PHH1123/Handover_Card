package com.handovercard.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "공통 오류 응답")
public record ApiErrorResponse(
        @Schema(description = "오류 발생 시각")
        Instant timestamp,

        @Schema(description = "HTTP 상태 코드")
        int status,

        @Schema(description = "HTTP 상태 문구", example = "Bad Request")
        String error,

        @Schema(description = "오류 메시지")
        String message,

        @Schema(description = "필드별 상세 오류 (검증 실패 시)")
        List<String> details
) {

    public static ApiErrorResponse of(int status, String error, String message) {
        return new ApiErrorResponse(Instant.now(), status, error, message, List.of());
    }

    public static ApiErrorResponse of(int status, String error, String message, List<String> details) {
        return new ApiErrorResponse(Instant.now(), status, error, message, details);
    }
}
