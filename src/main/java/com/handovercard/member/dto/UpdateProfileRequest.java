package com.handovercard.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @Schema(description = "새 표시 이름", example = "홍길동")
        @NotBlank String name
) {
}
