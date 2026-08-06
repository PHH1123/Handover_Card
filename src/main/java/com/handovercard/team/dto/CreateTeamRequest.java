package com.handovercard.team.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTeamRequest(
        @Schema(description = "만들 팀의 이름 (중복 불가)", example = "결제팀")
        @NotBlank @Size(max = 50) String name
) {
}
