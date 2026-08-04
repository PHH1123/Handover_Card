package com.handovercard.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @Schema(description = "현재 비밀번호")
        @NotBlank String currentPassword,

        @Schema(description = "새 비밀번호 (8~100자)")
        @NotBlank @Size(min = 8, max = 100) String newPassword
) {
}
