package com.handovercard.team;

import com.handovercard.card.HandoverCardRepository;
import com.handovercard.common.ResourceNotFoundException;
import com.handovercard.member.Member;
import com.handovercard.member.MemberRepository;
import com.handovercard.team.dto.JoinRequestResponse;
import com.handovercard.team.dto.TeamDetailResponse;
import com.handovercard.team.dto.TeamMemberResponse;
import com.handovercard.team.dto.TeamPageData;
import com.handovercard.team.dto.TeamResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 팀 생성/신청/승인. 한 회원은 한 팀에만 속하며, 팀 가입은 반드시 신청 → 팀장 승인을 거친다.
 *
 * <p>회원 엔티티를 수정해야 하므로 인자로 회원 ID만 받고 트랜잭션 안에서 다시 조회한다.
 * 인증 필터가 만들어 둔 {@code Member}는 이 시점에 준영속 상태라 그대로 변경해도 반영되지 않는다.
 */
@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamJoinRequestRepository joinRequestRepository;
    private final MemberRepository memberRepository;
    private final HandoverCardRepository handoverCardRepository;

    public TeamService(TeamRepository teamRepository, TeamJoinRequestRepository joinRequestRepository,
                        MemberRepository memberRepository, HandoverCardRepository handoverCardRepository) {
        this.teamRepository = teamRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.memberRepository = memberRepository;
        this.handoverCardRepository = handoverCardRepository;
    }

    @Transactional
    public TeamResponse createTeam(String name, Long memberId) {
        Member member = getMember(memberId);
        if (member.getTeam() != null) {
            throw new TeamOperationException("이미 팀에 소속되어 있어 새 팀을 만들 수 없습니다.");
        }
        if (!StringUtils.hasText(name)) {
            throw new TeamOperationException("팀 이름을 입력해 주세요.");
        }
        String trimmed = name.trim();
        if (teamRepository.existsByNameIgnoreCase(trimmed)) {
            throw new TeamOperationException("같은 이름의 팀이 이미 있습니다: " + trimmed);
        }

        Team team = teamRepository.save(new Team(trimmed, member));
        // 팀을 만든 사람은 팀장이자 팀원이 된다
        member.setTeam(team);

        // 다른 팀에 걸어둔 대기 중 신청은 의미가 없어지므로 정리한다
        joinRequestRepository.findByMemberAndStatus(member, TeamJoinRequestStatus.PENDING)
                .ifPresent(request -> request.setStatus(TeamJoinRequestStatus.REJECTED));
        return toTeamResponse(team);
    }

    @Transactional
    public void apply(Long teamId, Long memberId) {
        Member member = getMember(memberId);
        if (member.getTeam() != null) {
            throw new TeamOperationException("이미 팀에 소속되어 있습니다. 먼저 팀에서 나가야 신청할 수 있습니다.");
        }
        if (joinRequestRepository.findByMemberAndStatus(member, TeamJoinRequestStatus.PENDING).isPresent()) {
            throw new TeamOperationException("이미 승인 대기 중인 신청이 있습니다.");
        }
        joinRequestRepository.save(new TeamJoinRequest(getTeam(teamId), member));
    }

    @Transactional
    public void approve(Long requestId, Long leaderId) {
        TeamJoinRequest request = getPendingRequestLedBy(requestId, leaderId);
        Member applicant = request.getMember();
        if (applicant.getTeam() != null) {
            throw new TeamOperationException("이미 다른 팀에 소속된 회원입니다.");
        }
        request.setStatus(TeamJoinRequestStatus.APPROVED);
        applicant.setTeam(request.getTeam());
    }

    @Transactional
    public void reject(Long requestId, Long leaderId) {
        getPendingRequestLedBy(requestId, leaderId).setStatus(TeamJoinRequestStatus.REJECTED);
    }

    /** 팀장을 같은 팀의 다른 팀원에게 넘긴다. 넘긴 사람은 일반 팀원으로 남는다. */
    @Transactional
    public void transferLeadership(Long newLeaderId, Long currentLeaderId) {
        Member currentLeader = getMember(currentLeaderId);
        Team team = requireLedTeam(currentLeader);

        Member newLeader = getMember(newLeaderId);
        if (newLeader.getId().equals(currentLeader.getId())) {
            throw new TeamOperationException("이미 본인이 팀장입니다.");
        }
        if (newLeader.getTeam() == null || !newLeader.getTeam().getId().equals(team.getId())) {
            throw new TeamOperationException("같은 팀의 팀원에게만 팀장을 위임할 수 있습니다.");
        }
        team.setLeader(newLeader);
    }

    /** 팀장이 팀원을 팀에서 내보낸다. 내보낸 회원은 다시 신청할 수 있다. */
    @Transactional
    public void kickMember(Long targetMemberId, Long leaderId) {
        Member leader = getMember(leaderId);
        Team team = requireLedTeam(leader);

        Member target = getMember(targetMemberId);
        if (target.getId().equals(leader.getId())) {
            throw new TeamOperationException("팀장은 스스로를 내보낼 수 없습니다. 먼저 팀장을 위임해 주세요.");
        }
        if (target.getTeam() == null || !target.getTeam().getId().equals(team.getId())) {
            throw new TeamOperationException("같은 팀의 팀원이 아닙니다.");
        }
        target.setTeam(null);
    }

    /** 팀을 없앤다. 팀원은 모두 소속이 해제되고, 그 팀에 대한 신청 기록도 함께 삭제된다. */
    @Transactional
    public void deleteTeam(Long leaderId) {
        Member leader = getMember(leaderId);
        Team team = requireLedTeam(leader);

        memberRepository.findAllByTeamOrderByNameAsc(team).forEach(member -> member.setTeam(null));
        // 카드는 팀을 참조만 하므로 지우지 않고 연결만 끊는다. 소유자/수신자는 계속 카드를 볼 수 있다.
        handoverCardRepository.findAllByTeam(team).forEach(card -> card.setTeam(null));
        // 신청 기록이 팀을 참조하므로 팀보다 먼저 지워야 한다
        joinRequestRepository.deleteAllByTeam(team);
        teamRepository.delete(team);
    }

    @Transactional
    public void leave(Long memberId) {
        Member member = getMember(memberId);
        Team team = member.getTeam();
        if (team == null) {
            throw new TeamOperationException("소속된 팀이 없습니다.");
        }
        if (team.isLedBy(member)) {
            throw new TeamOperationException("팀장은 팀을 나갈 수 없습니다.");
        }
        member.setTeam(null);
    }

    /** 팀 화면이 필요한 정보를 한 번에 읽어 화면용 DTO로 만든다. */
    @Transactional(readOnly = true)
    public TeamPageData loadTeamPage(Long memberId) {
        Member me = getMember(memberId);
        Team myTeam = me.getTeam();

        if (myTeam == null) {
            List<TeamResponse> joinable = teamRepository.findAllByOrderByNameAsc().stream()
                    .map(this::toTeamResponse)
                    .toList();
            JoinRequestResponse pending = joinRequestRepository
                    .findByMemberAndStatus(me, TeamJoinRequestStatus.PENDING)
                    .map(this::toRequestResponse)
                    .orElse(null);
            List<JoinRequestResponse> history = joinRequestRepository.findAllByMemberOrderByCreatedAtDesc(me).stream()
                    .map(this::toRequestResponse)
                    .toList();
            return new TeamPageData(null, false, List.of(), List.of(), joinable, pending, history);
        }

        boolean leader = myTeam.isLedBy(me);
        List<JoinRequestResponse> pending = leader ? pendingRequestsOf(myTeam) : List.of();
        return new TeamPageData(toTeamResponse(myTeam), leader, membersOf(myTeam), pending, List.of(), null, List.of());
    }

    // ---------- REST API 조회 ----------

    @Transactional(readOnly = true)
    public List<TeamResponse> listTeams() {
        return teamRepository.findAllByOrderByNameAsc().stream().map(this::toTeamResponse).toList();
    }

    @Transactional(readOnly = true)
    public TeamDetailResponse getMyTeam(Long memberId) {
        Member me = getMember(memberId);
        Team team = requireTeam(me);
        return new TeamDetailResponse(team.getId(), team.getName(), team.getLeader().getName(),
                team.getLeader().getEmail(), team.isLedBy(me), membersOf(team), team.getCreatedAt());
    }

    /** 내 팀의 대기 중인 가입 신청 목록. 팀장만 조회할 수 있다. */
    @Transactional(readOnly = true)
    public List<JoinRequestResponse> listPendingRequests(Long leaderId) {
        return pendingRequestsOf(requireLedTeam(getMember(leaderId)));
    }

    @Transactional(readOnly = true)
    public List<JoinRequestResponse> listMyRequests(Long memberId) {
        return joinRequestRepository.findAllByMemberOrderByCreatedAtDesc(getMember(memberId)).stream()
                .map(this::toRequestResponse)
                .toList();
    }

    private Team requireLedTeam(Member member) {
        Team team = requireTeam(member);
        if (!team.isLedBy(member)) {
            throw new TeamOperationException("팀장만 할 수 있는 작업입니다.");
        }
        return team;
    }

    private Team requireTeam(Member member) {
        Team team = member.getTeam();
        if (team == null) {
            throw new ResourceNotFoundException("소속된 팀이 없습니다.");
        }
        return team;
    }

    private List<TeamMemberResponse> membersOf(Team team) {
        return memberRepository.findAllByTeamOrderByNameAsc(team).stream()
                .map(member -> new TeamMemberResponse(member.getId(), member.getName(), member.getEmail(),
                        team.isLedBy(member)))
                .toList();
    }

    private List<JoinRequestResponse> pendingRequestsOf(Team team) {
        return joinRequestRepository
                .findAllByTeamAndStatusOrderByCreatedAtAsc(team, TeamJoinRequestStatus.PENDING).stream()
                .map(this::toRequestResponse)
                .toList();
    }

    private TeamResponse toTeamResponse(Team team) {
        return new TeamResponse(team.getId(), team.getName(), team.getLeader().getName(),
                team.getLeader().getEmail(), memberRepository.findAllByTeamOrderByNameAsc(team).size(),
                team.getCreatedAt());
    }

    private JoinRequestResponse toRequestResponse(TeamJoinRequest request) {
        return new JoinRequestResponse(request.getId(), request.getTeam().getId(), request.getTeam().getName(),
                request.getMember().getName(), request.getMember().getEmail(), request.getStatus(),
                request.getCreatedAt());
    }

    private TeamJoinRequest getPendingRequestLedBy(Long requestId, Long leaderId) {
        TeamJoinRequest request = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Team join request not found: " + requestId));
        if (!request.getTeam().getLeader().getId().equals(leaderId)) {
            // 404, not 403 — 다른 팀의 신청이 존재한다는 사실 자체를 알리지 않는다
            throw new ResourceNotFoundException("Team join request not found: " + requestId);
        }
        if (request.getStatus() != TeamJoinRequestStatus.PENDING) {
            throw new TeamOperationException("이미 처리된 신청입니다.");
        }
        return request;
    }

    private Team getTeam(Long id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + id));
    }

    private Member getMember(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + id));
    }
}
