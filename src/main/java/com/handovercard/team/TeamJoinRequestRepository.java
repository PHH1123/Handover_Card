package com.handovercard.team;

import com.handovercard.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamJoinRequestRepository extends JpaRepository<TeamJoinRequest, Long> {

    List<TeamJoinRequest> findAllByTeamAndStatusOrderByCreatedAtAsc(Team team, TeamJoinRequestStatus status);

    List<TeamJoinRequest> findAllByMemberOrderByCreatedAtDesc(Member member);

    // 단건이어야 정상이지만 Optional로 받으면 데이터가 어긋났을 때 조회 자체가 500으로 죽는다.
    // 목록으로 받아 호출부가 판단하도록 한다.
    List<TeamJoinRequest> findAllByMemberAndStatus(Member member, TeamJoinRequestStatus status);

    /** 대기 표시 열이 비어 있는 옛 대기 신청. 시작 시 보정 대상이다. */
    List<TeamJoinRequest> findAllByStatusAndPendingMemberIdIsNullOrderByIdAsc(TeamJoinRequestStatus status);

    void deleteAllByMember(Member member);

    void deleteAllByTeam(Team team);
}
