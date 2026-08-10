package com.handovercard.member;

import com.handovercard.common.BaseEntity;
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

/**
 * 회원에 연결된 소셜 로그인 계정. 회원 하나가 Google과 GitHub를 모두 연결할 수 있어야 하므로
 * {@link Member}에 공급자 열을 하나 두지 않고 별도 테이블로 뺐다.
 *
 * <p>{@code (provider, provider_id)}에 걸린 유니크 제약이 "한 소셜 계정은 회원 한 명에게만 연결된다"를
 * 보장한다. 같은 소셜 계정으로 동시에 첫 로그인이 들어와도 둘 중 하나만 저장된다.
 */
@Entity
@Table(name = "social_accounts",
        uniqueConstraints = @UniqueConstraint(name = "uk_social_accounts_provider_provider_id",
                columnNames = {"provider", "provider_id"}))
@Getter
@NoArgsConstructor
public class SocialAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;

    /** 공급자가 발급한 불변 식별자(Google의 `sub`, GitHub의 숫자 `id`). 이메일과 달리 바뀌지 않는다. */
    @Column(name = "provider_id", nullable = false)
    private String providerId;

    /** 연결 시점에 공급자가 알려준 이메일. 표시·감사용이며 조회 키로 쓰지 않는다. */
    @Column(nullable = false)
    private String email;

    public SocialAccount(Member member, AuthProvider provider, String providerId, String email) {
        this.member = member;
        this.provider = provider;
        this.providerId = providerId;
        this.email = email;
    }
}
