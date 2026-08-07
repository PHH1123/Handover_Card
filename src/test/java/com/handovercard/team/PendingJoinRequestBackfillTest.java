package com.handovercard.team;

import com.handovercard.member.Member;
import com.handovercard.member.MemberRepository;
import com.handovercard.member.MemberRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대기 표시 열이 비어 있던 옛 데이터가 시작 시 보정되는지 확인한다.
 * 유니크 제약이 생기기 전에 저장된 행은 값이 없어 제약의 보호를 받지 못한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class PendingJoinRequestBackfillTest {

    @Autowired
    private PendingJoinRequestBackfill backfill;

    @Autowired
    private TeamJoinRequestRepository joinRequestRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member member(String label) {
        return memberRepository.save(new Member(
                label + "-" + System.nanoTime() + "@example.com", "hashed", label, MemberRole.USER));
    }

    private Team team(String label, Member leader) {
        return teamRepository.save(new Team(label + "-" + System.nanoTime(), leader));
    }

    /** 제약이 생기기 전 저장됐던 행처럼 대기 표시 없이 넣는다. */
    private void insertUnmarkedPending(Team team, Member member) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO team_join_requests
                    (created_at, updated_at, team_id, member_id, status, pending_member_id)
                VALUES (?, ?, ?, ?, 'PENDING', NULL)
                """, now, now, team.getId(), member.getId());
    }

    private List<TeamJoinRequest> pendingOf(Member member) {
        return joinRequestRepository.findAllByMemberAndStatus(member, TeamJoinRequestStatus.PENDING);
    }

    @Test
    void fillsInTheMarkerSoTheConstraintStartsProtectingOldRows() {
        Member leader = member("backfill-leader");
        Member applicant = member("backfill-applicant");
        Team team = team("backfill", leader);
        insertUnmarkedPending(team, applicant);

        backfill.run(null);

        List<TeamJoinRequest> pending = pendingOf(applicant);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getPendingMemberId()).isEqualTo(applicant.getId());
    }

    @Test
    void keepsTheOldestDuplicateAndRejectsTheRest() {
        Member leader = member("dup-leader");
        Member applicant = member("dup-applicant");
        Team teamA = team("dup-a", leader);
        Team teamB = team("dup-b", leader);
        insertUnmarkedPending(teamA, applicant);
        insertUnmarkedPending(teamB, applicant);

        backfill.run(null);

        // 대기 신청은 회원당 하나만 남아야 제약을 만족한다
        List<TeamJoinRequest> pending = pendingOf(applicant);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getTeam().getId()).isEqualTo(teamA.getId());
        assertThat(pending.get(0).getPendingMemberId()).isEqualTo(applicant.getId());

        assertThat(joinRequestRepository.findAllByMemberOrderByCreatedAtDesc(applicant))
                .filteredOn(request -> request.getStatus() == TeamJoinRequestStatus.REJECTED)
                .hasSize(1);
    }

    @Test
    void leavesAnAlreadyMarkedRequestAloneAndRejectsTheStrayOne() {
        Member leader = member("marked-leader");
        Member applicant = member("marked-applicant");
        Team teamA = team("marked-a", leader);
        Team teamB = team("marked-b", leader);
        // 정상 경로로 만들어진 대기 신청(표시 있음) + 옛 데이터(표시 없음)
        TeamJoinRequest marked = joinRequestRepository.save(new TeamJoinRequest(teamA, applicant));
        insertUnmarkedPending(teamB, applicant);

        backfill.run(null);

        List<TeamJoinRequest> pending = pendingOf(applicant);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getId()).isEqualTo(marked.getId());
    }

    @Test
    void doesNothingWhenThereIsNoOldData() {
        Member leader = member("noop-leader");
        Member applicant = member("noop-applicant");
        Team team = team("noop", leader);
        TeamJoinRequest request = joinRequestRepository.save(new TeamJoinRequest(team, applicant));

        backfill.run(null);
        backfill.run(null);

        assertThat(pendingOf(applicant)).extracting(TeamJoinRequest::getId).containsExactly(request.getId());
    }
}
