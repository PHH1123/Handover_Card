package com.handovercard.member;

import com.handovercard.auth.InvalidCredentialsException;
import com.handovercard.auth.RefreshTokenRepository;
import com.handovercard.card.HandoverCard;
import com.handovercard.card.HandoverCardRepository;
import com.handovercard.common.ResourceNotFoundException;
import com.handovercard.member.dto.MemberProfileResponse;
import com.handovercard.storage.AudioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberServiceTest {

    private MemberRepository memberRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private HandoverCardRepository handoverCardRepository;
    private AudioStorageService audioStorageService;
    private PasswordEncoder passwordEncoder;
    private MemberService service;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        handoverCardRepository = mock(HandoverCardRepository.class);
        audioStorageService = mock(AudioStorageService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new MemberService(memberRepository, refreshTokenRepository, handoverCardRepository,
                audioStorageService, passwordEncoder);
    }

    private Member member(long id) {
        Member member = new Member("owner@example.com", "hashed-old-pw", "Alex", MemberRole.USER);
        setId(member, id);
        return member;
    }

    private HandoverCard card(long id, Member owner, String audioFilePath) {
        HandoverCard card = new HandoverCard(owner, "Alex", "Minji", "en", "ko", audioFilePath, "sample.wav", "audio/wav", 10L);
        setId(card, id);
        return card;
    }

    private void setId(Object entity, long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void getProfileReturnsMappedResponse() {
        Member member = member(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        MemberProfileResponse response = service.getProfile(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("owner@example.com");
        assertThat(response.name()).isEqualTo("Alex");
    }

    @Test
    void getProfileThrowsNotFoundWhenMemberDoesNotExist() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateProfileChangesName() {
        Member member = member(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        MemberProfileResponse response = service.updateProfile(1L, "New Name");

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(member.getName()).isEqualTo("New Name");
    }

    @Test
    void changePasswordUpdatesEncodedPasswordWhenCurrentPasswordMatches() {
        Member member = member(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("old-pw", "hashed-old-pw")).thenReturn(true);
        when(passwordEncoder.encode("new-pw")).thenReturn("hashed-new-pw");

        service.changePassword(1L, "old-pw", "new-pw");

        assertThat(member.getPassword()).isEqualTo("hashed-new-pw");
    }

    @Test
    void changePasswordThrowsWhenCurrentPasswordIsWrong() {
        Member member = member(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("wrong-pw", "hashed-old-pw")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(1L, "wrong-pw", "new-pw"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(member.getPassword()).isEqualTo("hashed-old-pw");
    }

    @Test
    void deleteAccountRemovesRefreshTokensOwnedCardsAndNullsReceivedCards() {
        Member member = member(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        HandoverCard ownedCard = card(10L, member, "10_abc.wav");
        when(handoverCardRepository.findAllByOwner(member)).thenReturn(List.of(ownedCard));

        Member otherOwner = member(2L);
        HandoverCard receivedCard = card(20L, otherOwner, "20_def.wav");
        receivedCard.setReceiver(member);
        when(handoverCardRepository.findAllByReceiver(member)).thenReturn(List.of(receivedCard));

        service.deleteAccount(1L);

        verify(refreshTokenRepository).deleteAllByMember(member);
        verify(audioStorageService).delete("10_abc.wav");
        verify(handoverCardRepository).deleteAll(List.of(ownedCard));
        assertThat(receivedCard.getReceiver()).isNull();
        verify(memberRepository).delete(member);
    }

    @Test
    void deleteAccountThrowsNotFoundWhenMemberDoesNotExist() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteAccount(99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(memberRepository, never()).delete(any());
    }
}
