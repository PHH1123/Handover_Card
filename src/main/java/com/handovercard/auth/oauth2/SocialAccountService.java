package com.handovercard.auth.oauth2;

import com.handovercard.member.Member;
import com.handovercard.member.MemberRepository;
import com.handovercard.member.SocialAccount;
import com.handovercard.member.SocialAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 프로필을 우리 회원으로 바꾼다. 웹(리다이렉트) 로그인과 REST 로그인이 같은 규칙을 쓰도록
 * 두 경로 모두 이 서비스를 지난다.
 *
 * <p>규칙은 셋이다.
 * <ol>
 *   <li>이미 연결된 소셜 계정이면 그 회원으로 로그인한다. 공급자가 알려주는 이메일이 나중에 바뀌어도
 *       불변 식별자로 찾으므로 계정이 갈라지지 않는다.</li>
 *   <li>처음 보는 소셜 계정인데 같은 이메일의 회원이 있으면 그 회원에 연결한다.</li>
 *   <li>회원도 없으면 비밀번호 없는 회원을 새로 만든다.</li>
 * </ol>
 *
 * <p>2·3번은 <b>공급자가 확인해 준 이메일일 때만</b> 한다. 확인되지 않은 이메일을 그대로 믿으면 남의
 * 주소를 적은 소셜 계정으로 기존 회원에 올라타거나, 나중에 진짜 주인이 가입하지 못하게 막을 수 있다.
 */
@Service
public class SocialAccountService {

    private static final Logger log = LoggerFactory.getLogger(SocialAccountService.class);

    private final MemberRepository memberRepository;
    private final SocialAccountRepository socialAccountRepository;

    public SocialAccountService(MemberRepository memberRepository, SocialAccountRepository socialAccountRepository) {
        this.memberRepository = memberRepository;
        this.socialAccountRepository = socialAccountRepository;
    }

    @Transactional
    public Member resolve(OAuth2UserProfile profile) {
        return socialAccountRepository
                .findByProviderAndProviderId(profile.provider(), profile.providerId())
                .map(SocialAccount::getMember)
                .orElseGet(() -> link(profile));
    }

    private Member link(OAuth2UserProfile profile) {
        if (profile.email() == null || profile.email().isBlank()) {
            throw new UnverifiedSocialEmailException(
                    profile.provider().getDisplayName() + " 계정의 이메일을 가져오지 못했습니다.");
        }
        if (!profile.emailVerified()) {
            throw new UnverifiedSocialEmailException(
                    profile.provider().getDisplayName() + " 계정의 이메일이 확인되지 않았습니다.");
        }

        Member member = memberRepository.findByEmail(profile.email())
                .orElseGet(() -> memberRepository.save(Member.socialOnly(profile.email(), displayName(profile))));
        socialAccountRepository.save(
                new SocialAccount(member, profile.provider(), profile.providerId(), profile.email()));
        log.info("Linked {} account to member {}", profile.provider(), member.getId());
        return member;
    }

    /** 이름을 비공개로 둔 계정도 있어, 없으면 이메일 앞부분을 쓴다. */
    private String displayName(OAuth2UserProfile profile) {
        if (profile.name() != null && !profile.name().isBlank()) {
            return profile.name();
        }
        String email = profile.email();
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
