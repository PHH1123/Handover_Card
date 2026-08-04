package com.handovercard.member.dto;

import com.handovercard.member.Member;

import java.time.Instant;

public record MemberProfileResponse(
        Long id,
        String email,
        String name,
        String role,
        Instant createdAt
) {

    public static MemberProfileResponse from(Member member) {
        return new MemberProfileResponse(member.getId(), member.getEmail(), member.getName(),
                member.getRole().name(), member.getCreatedAt());
    }
}
