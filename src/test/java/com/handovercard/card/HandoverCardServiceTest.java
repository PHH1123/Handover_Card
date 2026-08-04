package com.handovercard.card;

import com.handovercard.common.ResourceNotFoundException;
import com.handovercard.member.Member;
import com.handovercard.member.MemberRole;
import com.handovercard.storage.AudioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HandoverCardServiceTest {

    private HandoverCardRepository repository;
    private HandoverCardService service;

    @BeforeEach
    void setUp() {
        repository = mock(HandoverCardRepository.class);
        AudioStorageService audioStorageService = mock(AudioStorageService.class);
        service = new HandoverCardService(repository, audioStorageService, new ObjectMapper());
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
    void getOwnedReturnsCardWhenRequesterIsOwner() {
        Member owner = member(1L, "owner@example.com");
        HandoverCard card = card(10L, owner);
        when(repository.findById(10L)).thenReturn(Optional.of(card));

        HandoverCard result = service.getOwned(10L, owner);

        assertThat(result).isSameAs(card);
    }

    @Test
    void getOwnedThrowsNotFoundWhenRequesterIsNotOwner() {
        Member owner = member(1L, "owner@example.com");
        Member stranger = member(2L, "stranger@example.com");
        HandoverCard card = card(10L, owner);
        when(repository.findById(10L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> service.getOwned(10L, stranger))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getOwnedThrowsNotFoundWhenCardDoesNotExist() {
        Member requester = member(1L, "owner@example.com");
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwned(99L, requester))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
