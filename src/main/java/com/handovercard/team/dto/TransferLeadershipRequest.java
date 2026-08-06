package com.handovercard.team.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record TransferLeadershipRequest(
        @Schema(description = "새 팀장이 될 팀원의 회원 ID", example = "5")
        @NotNull Long memberId
) {
}
