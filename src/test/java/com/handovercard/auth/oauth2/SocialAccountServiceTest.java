package com.handovercard.auth.oauth2;

import com.handovercard.member.AuthProvider;
import com.handovercard.member.Member;
import com.handovercard.member.MemberRepository;
import com.handovercard.member.MemberRole;
import com.handovercard.member.SocialAccount;
import com.handovercard.member.SocialAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 소셜 계정을 회원에 잇는 규칙을 검증한다. 이 규칙이 느슨해지면 남의 이메일을 적은 소셜 계정으로
 * 기존 회원에 로그인할 수 있게 되므로, 특히 "확인되지 않은 이메일은 막는다"를 못 박아 둔다.
 */
class SocialAccountServiceTest {

    private MemberRepository memberRepository;
    private SocialAccountRepository socialAccountRepository;
    private SocialAccountService socialAccountService;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        socialAccountRepository = mock(SocialAccountRepository.class);
        socialAccountService = new SocialAccountService(memberRepository, socialAccountRepository);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(socialAccountRepository.save(any(SocialAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private OAuth2UserProfile profile(String email, boolean verified) {
        return new OAuth2UserProfile(AuthProvider.GOOGLE, "google-1", email, "Alex", verified);
    }

    @Test
    void alreadyLinkedAccountLogsInWithoutTouchingEmail() {
        Member linked = new Member("old@example.com", "hash", "Alex", MemberRole.USER);
        when(socialAccountRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-1"))
                .thenReturn(Optional.of(new SocialAccount(linked, AuthProvider.GOOGLE, "google-1", "old@example.com")));

        // 공급자 쪽에서 이메일을 바꿔도 불변 식별자로 찾으므로 같은 회원이어야 한다
        Member resolved = socialAccountService.resolve(profile("new@example.com", true));

        assertThat(resolved).isSameAs(linked);
        verify(memberRepository, never()).save(any());
        verify(socialAccountRepository, never()).save(any());
    }

    @Test
    void verifiedEmailLinksToTheExistingMember() {
        Member existing = new Member("alex@example.com", "hash", "Alex", MemberRole.USER);
        when(socialAccountRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-1"))
                .thenReturn(Optional.empty());
        when(memberRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(existing));

        Member resolved = socialAccountService.resolve(profile("alex@example.com", true));

        assertThat(resolved).isSameAs(existing);
        verify(memberRepository, never()).save(any());

        ArgumentCaptor<SocialAccount> saved = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository).save(saved.capture());
        assertThat(saved.getValue().getMember()).isSameAs(existing);
        assertThat(saved.getValue().getProviderId()).isEqualTo("google-1");
    }

    @Test
    void unknownEmailCreatesAPasswordlessMember() {
        when(socialAccountRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-1"))
                .thenReturn(Optional.empty());
        when(memberRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

        Member resolved = socialAccountService.resolve(profile("new@example.com", true));

        assertThat(resolved.getEmail()).isEqualTo("new@example.com");
        assertThat(resolved.getName()).isEqualTo("Alex");
        assertThat(resolved.getRole()).isEqualTo(MemberRole.USER);
        // 비밀번호가 없으므로 이메일/비밀번호 로그인으로는 들어올 수 없다
        assertThat(resolved.hasPassword()).isFalse();
        verify(socialAccountRepository).save(any(SocialAccount.class));
    }

    @Test
    void unverifiedEmailIsRejectedAndNothingIsSaved() {
        when(socialAccountRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> socialAccountService.resolve(profile("alex@example.com", false)))
                .isInstanceOf(UnverifiedSocialEmailException.class);

        verify(memberRepository, never()).findByEmail(any());
        verify(memberRepository, never()).save(any());
        verify(socialAccountRepository, never()).save(any());
    }

    @Test
    void missingEmailIsRejected() {
        when(socialAccountRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> socialAccountService.resolve(profile(null, true)))
                .isInstanceOf(UnverifiedSocialEmailException.class);

        verify(socialAccountRepository, never()).save(any());
    }

    @Test
    void memberWithoutNameFallsBackToTheEmailLocalPart() {
        when(socialAccountRepository.findByProviderAndProviderId(AuthProvider.GITHUB, "42"))
                .thenReturn(Optional.empty());
        when(memberRepository.findByEmail("octocat@example.com")).thenReturn(Optional.empty());

        Member resolved = socialAccountService.resolve(
                new OAuth2UserProfile(AuthProvider.GITHUB, "42", "octocat@example.com", null, true));

        assertThat(resolved.getName()).isEqualTo("octocat");
    }
}
