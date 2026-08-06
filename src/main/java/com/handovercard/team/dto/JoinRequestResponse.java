package com.handovercard.team.dto;

import com.handovercard.team.TeamJoinRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record JoinRequestResponse(
        @Schema(description = "가입 신청 ID")
        Long id,

        @Schema(description = "신청 대상 팀 ID")
        Long teamId,

        @Schema(description = "신청 대상 팀 이름")
        String teamName,

        @Schema(description = "신청자 이름")
        String memberName,

        @Schema(description = "신청자 이메일")
        String memberEmail,

        @Schema(description = "처리 상태 (PENDING → APPROVED 또는 REJECTED)")
        TeamJoinRequestStatus status,

        @Schema(description = "신청 일시")
        Instant requestedAt
) {
}
