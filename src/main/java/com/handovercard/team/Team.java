package com.handovercard.team;

import com.handovercard.common.BaseEntity;
import com.handovercard.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "teams")
@Getter
@Setter
@NoArgsConstructor
public class Team extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    /** 팀을 만든 사람이 팀장이 되며, 팀 신청을 승인할 수 있는 유일한 회원이다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leader_id", nullable = false)
    private Member leader;

    public Team(String name, Member leader) {
        this.name = name;
        this.leader = leader;
    }

    public boolean isLedBy(Member member) {
        return member != null && leader.getId().equals(member.getId());
    }
}
