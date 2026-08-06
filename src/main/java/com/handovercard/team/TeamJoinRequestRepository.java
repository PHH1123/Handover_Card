package com.handovercard.team;

import com.handovercard.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamJoinRequestRepository extends JpaRepository<TeamJoinRequest, Long> {

    List<TeamJoinRequest> findAllByTeamAndStatusOrderByCreatedAtAsc(Team team, TeamJoinRequestStatus status);

    List<TeamJoinRequest> findAllByMemberOrderByCreatedAtDesc(Member member);

    Optional<TeamJoinRequest> findByMemberAndStatus(Member member, TeamJoinRequestStatus status);

    void deleteAllByMember(Member member);

    void deleteAllByTeam(Team team);
}
