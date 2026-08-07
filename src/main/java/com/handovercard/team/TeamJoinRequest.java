package com.handovercard.team;

import com.handovercard.common.BaseEntity;
import com.handovercard.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "team_join_requests",
        // 대기 중인 신청은 회원당 하나뿐이어야 한다. 자세한 이유는 pendingMemberId 참고.
        uniqueConstraints = @UniqueConstraint(name = "uk_join_request_pending_member",
                columnNames = "pending_member_id")
)
@Getter
@NoArgsConstructor
public class TeamJoinRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TeamJoinRequestStatus status;

    /**
     * 대기 중일 때만 회원 ID가 들어가고 처리되면 null이 되는 열. 여기에 유니크 제약을 걸어
     * "대기 중인 신청은 회원당 하나"를 DB 수준에서 보장한다.
     *
     * <p>{@code (member_id, status)}에 그냥 유니크를 걸면 거절된 이력이 쌓일 수 없어 재신청이 막히고,
     * MySQL에는 조건부 유니크 인덱스가 없다. null은 유니크 제약에서 서로 다른 값으로 취급되므로
     * 처리 완료된 신청은 몇 건이든 남을 수 있다.
     */
    @Column(name = "pending_member_id")
    private Long pendingMemberId;

    public TeamJoinRequest(Team team, Member member) {
        this.team = team;
        this.member = member;
        changeStatus(TeamJoinRequestStatus.PENDING);
    }

    public void approve() {
        changeStatus(TeamJoinRequestStatus.APPROVED);
    }

    public void reject() {
        changeStatus(TeamJoinRequestStatus.REJECTED);
    }

    public boolean isPending() {
        return status == TeamJoinRequestStatus.PENDING;
    }

    /** 상태와 대기 표시 열이 어긋나면 제약이 무의미해지므로 반드시 함께 바꾼다. */
    private void changeStatus(TeamJoinRequestStatus next) {
        this.status = next;
        this.pendingMemberId = next == TeamJoinRequestStatus.PENDING ? member.getId() : null;
    }
}
