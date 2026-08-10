package com.handovercard.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    /**
     * 회원을 함께 읽는다. 소셜 로그인은 트랜잭션이 끝난 뒤(성공 핸들러·권한 계산)에도 회원을 들여다보는데,
     * {@code member}가 지연 로딩 프록시로 남아 있으면 그 시점엔 세션이 없어 터진다(open-in-view=false).
     */
    @Query("select sa from SocialAccount sa join fetch sa.member "
            + "where sa.provider = :provider and sa.providerId = :providerId")
    Optional<SocialAccount> findByProviderAndProviderId(@Param("provider") AuthProvider provider,
                                                        @Param("providerId") String providerId);

    List<SocialAccount> findAllByMember(Member member);
}
