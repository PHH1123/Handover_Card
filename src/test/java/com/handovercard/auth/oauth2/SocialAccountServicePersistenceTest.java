package com.handovercard.auth.oauth2;

import com.handovercard.member.AuthProvider;
import com.handovercard.member.Member;
import com.handovercard.member.MemberRepository;
import com.handovercard.member.MemberRole;
import com.handovercard.member.SocialAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link SocialAccountService}가 돌려준 회원을 <b>트랜잭션 밖에서</b> 써도 되는지 본다.
 *
 * <p>{@link SocialAccountServiceTest}는 리포지토리를 모킹해서 이 부분을 볼 수 없다. 모킹한 리포지토리는
 * 평범한 객체를 돌려주지만 실제 JPA는 지연 로딩 프록시를 돌려주고, 소셜 로그인은 트랜잭션이 끝난 뒤
 * (권한 계산·성공 핸들러의 토큰 발급) 회원을 들여다보기 때문에 프록시가 그대로 새어 나가면 그때 터진다.
 * 그래서 이 테스트는 {@code @Transactional}을 붙이지 않는다 — 붙이면 세션이 열린 채라 재현되지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class SocialAccountServicePersistenceTest {

    private static final String PROVIDER_ID = "github-persistence-1";
    private static final String EMAIL = "persistence@example.com";

    @Autowired
    private SocialAccountService socialAccountService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @AfterEach
    void tearDown() {
        socialAccountRepository.findByProviderAndProviderId(AuthProvider.GITHUB, PROVIDER_ID)
                .ifPresent(socialAccountRepository::delete);
        memberRepository.findByEmail(EMAIL).ifPresent(memberRepository::delete);
    }

    private OAuth2UserProfile profile() {
        return new OAuth2UserProfile(AuthProvider.GITHUB, PROVIDER_ID, EMAIL, "Alex", true);
    }

    @Test
    void memberFromASecondLoginIsUsableAfterTheTransactionCloses() {
        Member firstLogin = socialAccountService.resolve(profile());
        assertThat(firstLogin.getEmail()).isEqualTo(EMAIL);

        // 두 번째부터는 이미 연결된 소셜 계정을 찾아오는 경로를 탄다. 여기서 회원이 초기화되지 않은
        // 프록시로 돌아오면, 로그인 성공 핸들러가 회원을 읽는 순간 LazyInitializationException이 난다.
        Member secondLogin = socialAccountService.resolve(profile());

        assertThatCode(() -> {
            assertThat(secondLogin.getId()).isEqualTo(firstLogin.getId());
            assertThat(secondLogin.getEmail()).isEqualTo(EMAIL);
            assertThat(secondLogin.getRole()).isEqualTo(MemberRole.USER);
        }).doesNotThrowAnyException();
    }
}
