package com.handovercard.member;

import com.handovercard.auth.InvalidCredentialsException;
import com.handovercard.auth.RefreshTokenRepository;
import com.handovercard.card.HandoverCard;
import com.handovercard.card.HandoverCardRepository;
import com.handovercard.common.ResourceNotFoundException;
import com.handovercard.member.dto.MemberLookupResponse;
import com.handovercard.member.dto.MemberLookupResult;
import com.handovercard.member.dto.MemberProfileResponse;
import com.handovercard.team.Team;
import com.handovercard.team.TeamJoinRequestRepository;
import com.handovercard.team.TeamOperationException;
import com.handovercard.team.TeamRepository;
import com.handovercard.storage.AudioStorageService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final HandoverCardRepository handoverCardRepository;
    private final AudioStorageService audioStorageService;
    private final PasswordEncoder passwordEncoder;
    private final TeamRepository teamRepository;
    private final TeamJoinRequestRepository teamJoinRequestRepository;

    public MemberService(MemberRepository memberRepository, RefreshTokenRepository refreshTokenRepository,
                          HandoverCardRepository handoverCardRepository, AudioStorageService audioStorageService,
                          PasswordEncoder passwordEncoder, TeamRepository teamRepository,
                          TeamJoinRequestRepository teamJoinRequestRepository) {
        this.memberRepository = memberRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.handoverCardRepository = handoverCardRepository;
        this.audioStorageService = audioStorageService;
        this.passwordEncoder = passwordEncoder;
        this.teamRepository = teamRepository;
        this.teamJoinRequestRepository = teamJoinRequestRepository;
    }

    /** 자동완성 대상이 무한정 늘어나지 않도록 상한을 둔다. 동명이인이 이보다 많으면 직접 입력해야 한다. */
    private static final int MAX_LOOKUP_RESULTS = 10;
    private static final int MIN_LOOKUP_NAME_LENGTH = 2;

    @Transactional(readOnly = true)
    public MemberProfileResponse getProfile(Long memberId) {
        return MemberProfileResponse.from(getById(memberId));
    }

    /**
     * 요청자와 같은 팀 안에서 이름이 정확히 일치하는 회원들의 이메일을 찾는다 (수신자 입력 자동완성용).
     * 이메일이 노출되는 조회라 같은 팀으로 한정하고, 부분 일치나 너무 짧은 이름은 허용하지 않는다.
     * 팀이 없으면 후보를 주지 않고 화면에서 직접 입력하도록 안내한다.
     */
    @Transactional(readOnly = true)
    public MemberLookupResult lookupTeammatesByName(String name, Long requesterId) {
        Team team = getById(requesterId).getTeam();
        if (team == null) {
            return MemberLookupResult.withoutTeam();
        }
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() < MIN_LOOKUP_NAME_LENGTH) {
            return new MemberLookupResult(true, team.getName(), List.of());
        }
        List<MemberLookupResponse> matches = memberRepository.findByNameIgnoreCaseAndTeam(trimmed, team).stream()
                .limit(MAX_LOOKUP_RESULTS)
                .map(MemberLookupResponse::from)
                .toList();
        return new MemberLookupResult(true, team.getName(), matches);
    }

    @Transactional
    public MemberProfileResponse updateProfile(Long memberId, String newName) {
        Member member = getById(memberId);
        member.setName(newName);
        return MemberProfileResponse.from(member);
    }

    @Transactional
    public void changePassword(Long memberId, String currentPassword, String newPassword) {
        Member member = getById(memberId);
        if (!passwordEncoder.matches(currentPassword, member.getPassword())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }
        member.setPassword(passwordEncoder.encode(newPassword));
    }

    @Transactional
    public void deleteAccount(Long memberId) {
        Member member = getById(memberId);

        // 팀장을 지우면 팀이 리더 없이 남는다. 위임/삭제로 팀을 정리한 뒤에만 탈퇴할 수 있다.
        if (teamRepository.existsByLeader(member)) {
            throw new TeamOperationException(
                    "팀장은 계정을 삭제할 수 없습니다. 먼저 팀장을 위임하거나 팀을 삭제해 주세요.");
        }
        // 가입 신청 이력이 회원을 참조하므로 함께 지워야 한다
        teamJoinRequestRepository.deleteAllByMember(member);

        refreshTokenRepository.deleteAllByMember(member);

        List<HandoverCard> ownedCards = handoverCardRepository.findAllByOwner(member);
        ownedCards.forEach(card -> audioStorageService.delete(card.getAudioFilePath()));
        handoverCardRepository.deleteAll(ownedCards);

        List<HandoverCard> receivedCards = handoverCardRepository.findAllByReceiver(member);
        receivedCards.forEach(card -> card.setReceiver(null));

        memberRepository.delete(member);
    }

    private Member getById(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + id));
    }
}
