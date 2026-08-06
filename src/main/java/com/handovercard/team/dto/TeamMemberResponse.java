package com.handovercard.team.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TeamMemberResponse(
        @Schema(description = "회원 ID (팀장 위임·추방 대상 지정에 사용)")
        Long memberId,

        @Schema(description = "팀원 이름")
        String name,

        @Schema(description = "팀원 이메일")
        String email,

        @Schema(description = "팀장 여부")
        boolean leader
) {
}
