package com.handovercard.card;

import com.handovercard.card.dto.SummaryDto;
import com.handovercard.card.dto.SummaryEntryDto;
import com.handovercard.card.dto.UpdateHandoverResultRequest;
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
        when(memberRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(repository.findAllAccessibleTo(requester, null, pageable)).thenReturn(new PageImpl<>(List.of(card)));

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

    // ---------- 결과 수정 ----------

    private HandoverCard completedCard(long id, Member owner) {
        HandoverCard card = card(id, owner);
        card.setStatus(HandoverStatus.COMPLETED);
        card.setTranscript("원본 전사");
        card.setTranslatedText("original translation");
        card.setSummaryJson("{\"keyPoints\":[{\"source\":\"원본 핵심\",\"target\":\"original key point\"}],"
                + "\"actionItems\":[],\"blockers\":[]}");
        return card;
    }

    @Test
    void updateResultOnlyChangesTheFieldsThatWereSent() {
        Member owner = member(1L, "owner@example.com");
        HandoverCard card = completedCard(10L, owner);
        when(repository.findById(10L)).thenReturn(Optional.of(card));

        service.updateResult(10L, owner, new UpdateHandoverResultRequest("  고친 전사  ", null, null));

        assertThat(card.getTranscript()).isEqualTo("고친 전사");
        // 보내지 않은 항목은 그대로 남는다
        assertThat(card.getTranslatedText()).isEqualTo("original translation");
        assertThat(card.getSummaryJson()).contains("original key point");
    }

    @Test
    void updateResultReplacesTheWholeSummary() {
        Member owner = member(1L, "owner@example.com");
        HandoverCard card = completedCard(10L, owner);
        when(repository.findById(10L)).thenReturn(Optional.of(card));

        SummaryDto summary = new SummaryDto(
                List.of(new SummaryEntryDto("고친 핵심", "edited key point")),
                List.of(new SummaryEntryDto("할 일", "todo")),
                List.of());
        service.updateResult(10L, owner, new UpdateHandoverResultRequest(null, null, summary));

        assertThat(card.getSummaryJson())
                .contains("edited key point")
                .contains("todo")
                .doesNotContain("original key point");
    }

    /** 화면의 빈 입력 칸이 그대로 저장되면 요약에 빈 항목이 쌓인다. */
    @Test
    void updateResultDropsBlankSummaryEntries() {
        Member owner = member(1L, "owner@example.com");
        HandoverCard card = completedCard(10L, owner);
        when(repository.findById(10L)).thenReturn(Optional.of(card));

        SummaryDto summary = new SummaryDto(
                java.util.Arrays.asList(new SummaryEntryDto(" 남는 항목 ", " kept "), new SummaryEntryDto("  ", ""), null),
                null, null);
        service.updateResult(10L, owner, new UpdateHandoverResultRequest(null, null, summary));

        assertThat(card.getSummaryJson())
                .contains("\"source\":\"남는 항목\"")
                .contains("\"target\":\"kept\"")
                .contains("\"actionItems\":[]")
                .contains("\"blockers\":[]");
        assertThat(card.getSummaryJson().split("\"source\"", -1)).hasSize(2);
    }

    /**
     * 처리 중인 카드를 고치면 뒤이어 끝난 파이프라인이 결과를 덮어써 수정이 조용히 사라진다.
     * 그래서 COMPLETED가 아닌 카드는 아예 받지 않는다.
     */
    @Test
    void updateResultThrowsInvalidStateWhenCardIsNotCompleted() {
        Member owner = member(1L, "owner@example.com");
        HandoverCard card = completedCard(10L, owner);
        card.setStatus(HandoverStatus.SUMMARIZING);
        when(repository.findById(10L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> service.updateResult(10L, owner,
                new UpdateHandoverResultRequest("고친 전사", null, null)))
                .isInstanceOf(InvalidCardStateException.class);
        assertThat(card.getTranscript()).isEqualTo("원본 전사");
    }

    @Test
    void updateResultThrowsNotFoundWhenRequesterIsNotTheOwner() {
        Member owner = member(1L, "owner@example.com");
        Member stranger = member(2L, "stranger@example.com");
        HandoverCard card = completedCard(10L, owner);
        when(repository.findById(10L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> service.updateResult(10L, stranger,
                new UpdateHandoverResultRequest("남의 카드", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(card.getTranscript()).isEqualTo("원본 전사");
    }

    /** 수신자는 카드를 볼 수만 있고 결과를 고칠 수는 없다. */
    @Test
    void updateResultThrowsNotFoundWhenRequesterIsOnlyTheReceiver() {
        Member owner = member(1L, "owner@example.com");
        Member receiver = member(2L, "receiver@example.com");
        HandoverCard card = completedCard(10L, owner);
        card.setReceiver(receiver);
        when(repository.findById(10L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> service.updateResult(10L, receiver,
                new UpdateHandoverResultRequest("수신자 수정", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
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
