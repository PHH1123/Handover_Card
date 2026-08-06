package com.handovercard.team.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record TeamDetailResponse(
        @Schema(description = "팀 ID")
        Long id,

        @Schema(description = "팀 이름")
        String name,

        @Schema(description = "팀장 이름")
        String leaderName,

        @Schema(description = "팀장 이메일")
        String leaderEmail,

        @Schema(description = "요청한 회원이 이 팀의 팀장인지 여부")
        boolean leader,

        @Schema(description = "팀장을 포함한 팀원 목록")
        List<TeamMemberResponse> members,

        @Schema(description = "생성 일시")
        Instant createdAt
) {
}
