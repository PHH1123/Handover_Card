package com.handovercard.team;

import com.handovercard.security.CustomUserDetails;
import com.handovercard.team.dto.CreateTeamRequest;
import com.handovercard.team.dto.JoinRequestResponse;
import com.handovercard.team.dto.TeamDetailResponse;
import com.handovercard.team.dto.TeamResponse;
import com.handovercard.team.dto.TransferLeadershipRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@Tag(name = "팀", description = "팀 생성, 가입 신청, 팀장의 신청 승인/거절")
@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @Operation(summary = "팀 생성", description = "새 팀을 만들고 요청한 회원이 팀장이 됩니다. "
            + "이미 팀에 소속되어 있거나 같은 이름의 팀이 있으면 409를 반환합니다.")
    @PostMapping
    public ResponseEntity<TeamResponse> create(@Valid @RequestBody CreateTeamRequest request,
                                                 @AuthenticationPrincipal CustomUserDetails principal) {
        TeamResponse team = teamService.createTeam(request.name(), principal.getMember().getId());
        return ResponseEntity.created(URI.create("/api/teams/" + team.id())).body(team);
    }

    @Operation(summary = "팀 목록 조회", description = "가입 신청 대상을 고르기 위한 전체 팀 목록입니다.")
    @GetMapping
    public ResponseEntity<List<TeamResponse>> list() {
        return ResponseEntity.ok(teamService.listTeams());
    }

    @Operation(summary = "내 팀 조회", description = "내가 속한 팀과 팀원 목록을 반환합니다. 소속된 팀이 없으면 404를 반환합니다.")
    @GetMapping("/me")
    public ResponseEntity<TeamDetailResponse> myTeam(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(teamService.getMyTeam(principal.getMember().getId()));
    }

    @Operation(summary = "팀 나가기", description = "내가 속한 팀에서 나갑니다. 팀장은 나갈 수 없어 409를 반환합니다.")
    @DeleteMapping("/me/membership")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal CustomUserDetails principal) {
        teamService.leave(principal.getMember().getId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "팀장 위임", description = "같은 팀의 다른 팀원에게 팀장을 넘깁니다. 넘긴 사람은 일반 팀원으로 남습니다. "
            + "팀장이 아니거나 대상이 같은 팀원이 아니면 409를 반환합니다.")
    @PutMapping("/me/leader")
    public ResponseEntity<Void> transferLeadership(@Valid @RequestBody TransferLeadershipRequest request,
                                                     @AuthenticationPrincipal CustomUserDetails principal) {
        teamService.transferLeadership(request.memberId(), principal.getMember().getId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "팀원 추방", description = "팀장이 팀원을 팀에서 내보냅니다. 내보낸 회원은 다시 가입 신청할 수 있습니다. "
            + "팀장 자신은 내보낼 수 없습니다.")
    @DeleteMapping("/me/members/{memberId}")
    public ResponseEntity<Void> kickMember(@PathVariable Long memberId,
                                             @AuthenticationPrincipal CustomUserDetails principal) {
        teamService.kickMember(memberId, principal.getMember().getId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "팀 삭제", description = "팀장이 팀을 없앱니다. 모든 팀원의 소속이 해제되고 해당 팀의 가입 신청 기록도 삭제됩니다. "
            + "인수인계 카드는 영향을 받지 않습니다.")
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteTeam(@AuthenticationPrincipal CustomUserDetails principal) {
        teamService.deleteTeam(principal.getMember().getId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "팀 가입 신청", description = "해당 팀에 가입을 신청합니다. 팀장이 승인해야 팀원이 됩니다. "
            + "이미 팀에 속해 있거나 대기 중인 신청이 있으면 409를 반환합니다.")
    @PostMapping("/{teamId}/join-requests")
    public ResponseEntity<Void> apply(@PathVariable Long teamId,
                                        @AuthenticationPrincipal CustomUserDetails principal) {
        teamService.apply(teamId, principal.getMember().getId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "내 팀의 가입 신청 목록", description = "대기 중인 신청만 반환합니다. 팀장만 조회할 수 있습니다.")
    @GetMapping("/me/join-requests")
    public ResponseEntity<List<JoinRequestResponse>> pendingRequests(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(teamService.listPendingRequests(principal.getMember().getId()));
    }

    @Operation(summary = "내 가입 신청 내역", description = "내가 낸 신청의 처리 상태를 최신순으로 반환합니다.")
    @GetMapping("/my-join-requests")
    public ResponseEntity<List<JoinRequestResponse>> myRequests(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(teamService.listMyRequests(principal.getMember().getId()));
    }

    @Operation(summary = "가입 신청 취소", description = "내가 낸 대기 중인 신청을 철회합니다. 신청 기록은 남지 않아 바로 다시 신청할 수 있습니다. "
            + "내가 낸 신청이 아니거나 없는 신청이면 404, 이미 승인/거절된 신청이면 409를 반환합니다.")
    @DeleteMapping("/my-join-requests/{requestId}")
    public ResponseEntity<Void> cancelMyRequest(@PathVariable Long requestId,
                                                  @AuthenticationPrincipal CustomUserDetails principal) {
        teamService.cancelMyRequest(requestId, principal.getMember().getId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "가입 신청 승인", description = "팀장만 승인할 수 있습니다. 다른 팀의 신청이거나 없는 신청이면 404, "
            + "이미 처리된 신청이면 409를 반환합니다.")
    @PostMapping("/join-requests/{requestId}/approve")
    public ResponseEntity<Void> approve(@PathVariable Long requestId,
                                          @AuthenticationPrincipal CustomUserDetails principal) {
        teamService.approve(requestId, principal.getMember().getId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "가입 신청 거절", description = "팀장만 거절할 수 있습니다.")
    @PostMapping("/join-requests/{requestId}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long requestId,
                                         @AuthenticationPrincipal CustomUserDetails principal) {
        teamService.reject(requestId, principal.getMember().getId());
        return ResponseEntity.noContent().build();
    }
}
