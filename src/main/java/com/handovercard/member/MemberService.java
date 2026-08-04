package com.handovercard.member;

import com.handovercard.auth.InvalidCredentialsException;
import com.handovercard.auth.RefreshTokenRepository;
import com.handovercard.card.HandoverCard;
import com.handovercard.card.HandoverCardRepository;
import com.handovercard.common.ResourceNotFoundException;
import com.handovercard.member.dto.MemberProfileResponse;
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

    public MemberService(MemberRepository memberRepository, RefreshTokenRepository refreshTokenRepository,
                          HandoverCardRepository handoverCardRepository, AudioStorageService audioStorageService,
                          PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.handoverCardRepository = handoverCardRepository;
        this.audioStorageService = audioStorageService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public MemberProfileResponse getProfile(Long memberId) {
        return MemberProfileResponse.from(getById(memberId));
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
