package com.handovercard.auth.dto;

import com.handovercard.member.Member;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record MemberResponse(
        @Schema(description = "회원 ID")
        Long id,

        @Schema(description = "이메일")
        String email,

        @Schema(description = "이름")
        String name,

        @Schema(description = "권한 (USER 또는 ADMIN)")
        String role,

        @Schema(description = "가입 일시")
        Instant createdAt
) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getEmail(), member.getName(),
                member.getRole().name(), member.getCreatedAt());
    }
}
