package com.handovercard.member.dto;

import com.handovercard.member.Member;

/** 수신자 이메일 자동완성용 최소 정보. 이름/이메일 외에는 노출하지 않는다. */
public record MemberLookupResponse(
        String name,
        String email
) {
    public static MemberLookupResponse from(Member member) {
        return new MemberLookupResponse(member.getName(), member.getEmail());
    }
}
