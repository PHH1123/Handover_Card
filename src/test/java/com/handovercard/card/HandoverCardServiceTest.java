package com.handovercard.card;

import com.handovercard.common.ResourceNotFoundException;
import com.handovercard.member.Member;
import com.handovercard.member.MemberRepository;
import com.handovercard.member.MemberRole;
import com.handovercard.storage.AudioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HandoverCardServiceTest {

    private HandoverCardRepository repository;
    private MemberRepository memberRepository;
    private AudioStorageService audioStorageService;
    private HandoverCardService service;

    @BeforeEach
    void setUp() {
        repository = mock(HandoverCardRepository.class);
        memberRepository = mock(MemberRepository.class);
        audioStorageService = mock(AudioStorageService.class);
        service = new HandoverCardService(repository, memberRepository, audioStorageService, new ObjectMapper());
    }

    private Member member(long id, String email) {
        Member member = new Member(email, "hashed-pw", "Name", MemberRole.USER);
        setId(member, id);
        return member;
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

    private HandoverCard card(long id, Member owner) {
        HandoverCard card = new HandoverCard(owner, "Alex", "Minji", "en", "ko", null, null, null, null);
        setId(card, id);
        return card;
    }

    @Test
    void getAccessibleReturnsCardWhenRequesterIsOwner() {
        Member owner = member(1L, "owner@example.com");
        HandoverCard card = card(10L, owner);
        when(repository.findById(10L)).thenReturn(Optional.of(card));

        HandoverCard result = service.getAccessible(10L, owner);

        assertThat(result).isSameAs(card);
    }

    @Test
    void getAccessibleReturnsCardWhenRequesterIsLinkedReceiver() {
        Member owner = member(1L, "owner@example.com");
        Member receiver = member(2L, "receiver@example.com");
        HandoverCard card = card(10L, owner);
        card.setReceiver(receiver);
        when(repository.findById(10L)).thenReturn(Optional.of(card));

        HandoverCard result = service.getAccessible(10L, receiver);

        assertThat(result).isSameAs(card);
    }

    @Test
    void getAccessibleThrowsNotFoundWhenRequesterIsNeitherOwnerNorReceiver() {
        Member owner = member(1L, "owner@example.com");
        Member stranger = member(2L, "stranger@example.com");
        HandoverCard card = card(10L, owner);
        when(repository.findById(10L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> service.getAccessible(10L, stranger))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAccessibleThrowsNotFoundWhenCardDoesNotExist() {
        Member requester = member(1L, "owner@example.com");
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAccessible(99L, requester))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listAccessibleDelegatesToRepositoryQuery() {
        Member requester = member(1L, "owner@example.com");
        HandoverCard card = card(10L, requester);
        Pageable pageable = PageRequest.of(0, 20);
        when(repository.findAllAccessibleTo(requester, pageable)).thenReturn(new PageImpl<>(List.of(card)));

        Page<HandoverCard> result = service.listAccessible(requester, pageable);

        assertThat(result.getContent()).containsExactly(card);
    }

    @Test
    void deleteRemovesCardAndAudioFileWhenRequesterIsOwner() {
        Member owner = member(1L, "owner@example.com");
        HandoverCard card = card(10L, owner);
        card.setAudioFilePath("10_abc.wav");
        when(repository.findById(10L)).thenReturn(Optional.of(card));

        service.delete(10L, owner);

        verify(audioStorageService).delete("10_abc.wav");
        verify(repository).delete(card);
    }

    @Test
    void deleteThrowsNotFoundWhenRequesterIsNotOwner() {
        Member owner = member(1L, "owner@example.com");
        Member stranger = member(2L, "stranger@example.com");
        HandoverCard card = card(10L, owner);
        when(repository.findById(10L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> service.delete(10L, stranger))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).delete(card);
    }

    @Test
    void deleteThrowsNotFoundWhenRequesterIsOnlyTheReceiver() {
        Member owner = member(1L, "owner@example.com");
        Member receiver = member(2L, "receiver@example.com");
        HandoverCard card = card(10L, owner);
        card.setReceiver(receiver);
        when(repository.findById(10L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> service.delete(10L, receiver))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).delete(card);
    }

    @Test
    void reprocessResetsFailedCardToReceived() {
        Member owner = member(1L, "owner@example.com");
        HandoverCard card = card(10L, owner);
        card.setStatus(HandoverStatus.FAILED);
        card.setErrorMessage("boom");
        when(repository.findById(10L)).thenReturn(Optional.of(card));

        HandoverCard result = service.reprocess(10L, owner);

        assertThat(result.getStatus()).isEqualTo(HandoverStatus.RECEIVED);
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    void reprocessThrowsInvalidStateWhenCardIsNotFailed() {
        Member owner = member(1L, "owner@example.com");
        HandoverCard card = card(10L, owner);
        card.setStatus(HandoverStatus.COMPLETED);
        when(repository.findById(10L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> service.reprocess(10L, owner))
                .isInstanceOf(InvalidCardStateException.class);
    }

    @Test
    void reprocessThrowsNotFoundWhenRequesterIsNotOwner() {
        Member owner = member(1L, "owner@example.com");
        Member stranger = member(2L, "stranger@example.com");
        HandoverCard card = card(10L, owner);
        card.setStatus(HandoverStatus.FAILED);
        when(repository.findById(10L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> service.reprocess(10L, stranger))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
