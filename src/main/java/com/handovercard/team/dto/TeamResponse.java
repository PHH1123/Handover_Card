package com.handovercard.team.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record TeamResponse(
        @Schema(description = "팀 ID")
        Long id,

        @Schema(description = "팀 이름")
        String name,

        @Schema(description = "팀장 이름")
        String leaderName,

        @Schema(description = "팀장 이메일")
        String leaderEmail,

        @Schema(description = "팀장을 포함한 팀원 수")
        int memberCount,

        @Schema(description = "생성 일시")
        Instant createdAt
) {
}
