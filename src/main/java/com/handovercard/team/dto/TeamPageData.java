package com.handovercard.team.dto;

import java.util.List;

/**
 * 팀 화면이 필요로 하는 모든 정보를 한 번의 읽기 트랜잭션에서 만들어 담는다.
 * {@code open-in-view: false}라 엔티티를 그대로 넘기면 화면에서 지연 로딩이 터진다.
 * 항목 자체는 REST API와 같은 DTO를 재사용한다.
 */
public record TeamPageData(
        TeamResponse myTeam,
        boolean leader,
        List<TeamMemberResponse> members,
        List<JoinRequestResponse> pendingRequests,
        List<TeamResponse> joinableTeams,
        JoinRequestResponse myPendingRequest,
        List<JoinRequestResponse> myRequests
) {
}
