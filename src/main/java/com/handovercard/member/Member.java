package com.handovercard.member;

import com.handovercard.common.BaseEntity;
import com.handovercard.team.Team;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "members")
@Getter
@Setter
@NoArgsConstructor
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    /**
     * BCrypt 해시. 소셜 로그인으로만 가입한 회원은 비밀번호가 없어 비어 있다.
     * (기존 DB는 이 열이 NOT NULL이라 README의 마이그레이션을 한 번 실행해야 한다.)
     */
    @Column
    private String password;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    /** 소속 팀. 한 회원은 한 팀에만 속하며, 팀장이 신청을 승인해야 채워진다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    public Member(String email, String password, String name, MemberRole role) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.role = role;
    }

    /** 소셜 로그인으로 처음 들어온 회원. 비밀번호가 없으므로 이메일/비밀번호 로그인은 할 수 없다. */
    public static Member socialOnly(String email, String name) {
        return new Member(email, null, name, MemberRole.USER);
    }

    /** 이메일/비밀번호로 로그인할 수 있는 회원인지. 소셜 전용 회원은 {@code false}. */
    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }
}
