package com.handovercard.member.dto;

import java.util.List;

/**
 * 수신자 이메일 자동완성 결과.
 * 팀이 없어서 후보를 못 찾은 경우와, 팀은 있는데 동명의 팀원이 없는 경우를 화면에서 구분해야 해서
 * 목록만 주지 않고 소속 팀 정보를 함께 내려준다.
 */
public record MemberLookupResult(
        boolean hasTeam,
        String teamName,
        List<MemberLookupResponse> matches
) {
    public static MemberLookupResult withoutTeam() {
        return new MemberLookupResult(false, null, List.of());
    }
}
