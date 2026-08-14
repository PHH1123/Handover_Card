package com.handovercard.card;

import tools.jackson.databind.ObjectMapper;
import com.handovercard.card.dto.HandoverCardUploadRequest;
import com.handovercard.card.dto.SummaryDto;
import com.handovercard.card.dto.SummaryEntryDto;
import com.handovercard.card.dto.UpdateHandoverResultRequest;
import com.handovercard.common.ResourceNotFoundException;
import com.handovercard.member.Member;
import com.handovercard.member.MemberRepository;
import com.handovercard.storage.AudioStorageService;
import com.handovercard.storage.StoredAudio;
import com.handovercard.summarization.SummaryResult;
import com.handovercard.team.Team;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class HandoverCardService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    private final HandoverCardRepository repository;
    private final MemberRepository memberRepository;
    private final AudioStorageService audioStorageService;
    private final ObjectMapper objectMapper;

    public HandoverCardService(HandoverCardRepository repository, MemberRepository memberRepository,
                                AudioStorageService audioStorageService, ObjectMapper objectMapper) {
        this.repository = repository;
        this.memberRepository = memberRepository;
        this.audioStorageService = audioStorageService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public HandoverCard createAndPersist(HandoverCardUploadRequest request, Member owner) {
        HandoverCard card = new HandoverCard(
                owner,
                request.getSenderName(),
                request.getReceiverName(),
                request.getSourceLanguage(),
                request.getTargetLanguage(),
                null, null, null, null
        );
        // 작성 시점의 소속 팀을 박아 둬야 이후 팀 이동이 과거 카드 접근에 영향을 주지 않는다
        card.setTeam(teamOf(owner));

        if (StringUtils.hasText(request.getReceiverEmail())) {
            memberRepository.findByEmail(request.getReceiverEmail()).ifPresent(card::setReceiver);
        }
        card = repository.save(card);

        StoredAudio stored = audioStorageService.store(request.getAudio(), card.getId());
        card.setAudioFilePath(stored.relativePath());
        card.setOriginalFilename(stored.originalFilename());
        card.setContentType(stored.contentType());
        card.setFileSizeBytes(stored.sizeBytes());

        return repository.save(card);
    }

    @Transactional(readOnly = true)
    public HandoverCard get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Handover card not found: " + id));
    }

    @Transactional(readOnly = true)
    public HandoverCard getAccessible(Long id, Member requester) {
        HandoverCard card = get(id);
        if (!isAccessibleTo(card, requester)) {
            // 404, not 403 — avoids confirming to other users that this card ID exists
            throw new ResourceNotFoundException("Handover card not found: " + id);
        }
        return card;
    }

    @Transactional(readOnly = true)
    public Page<HandoverCard> listAccessible(Member requester, Pageable pageable) {
        return repository.findAllAccessibleTo(requester, teamOf(requester), pageable);
    }

    private boolean isAccessibleTo(HandoverCard card, Member requester) {
        if (card.getOwner().getId().equals(requester.getId())) {
            return true;
        }
        if (card.getReceiver() != null && card.getReceiver().getId().equals(requester.getId())) {
            return true;
        }
        // 카드가 만들어진 팀의 팀원이면 함께 볼 수 있다
        Team requesterTeam = teamOf(requester);
        return card.getTeam() != null && requesterTeam != null
                && card.getTeam().getId().equals(requesterTeam.getId());
    }

    /**
     * 회원의 소속 팀을 다시 읽어 온다.
     * 인증 필터가 넘겨준 Member는 준영속이라 지연 로딩된 team을 그대로 건드리면 예외가 난다.
     */
    private Team teamOf(Member member) {
        return memberRepository.findById(member.getId())
                .map(Member::getTeam)
                .orElse(null);
    }

    @Transactional
    public void delete(Long id, Member requester) {
        HandoverCard card = getOwned(id, requester);
        audioStorageService.delete(card.getAudioFilePath());
        repository.delete(card);
    }

    @Transactional
    public HandoverCard reprocess(Long id, Member requester) {
        HandoverCard card = getOwned(id, requester);
        if (card.getStatus() != HandoverStatus.FAILED) {
            throw new InvalidCardStateException("Only failed handover cards can be reprocessed: " + id);
        }
        card.setStatus(HandoverStatus.RECEIVED);
        card.setErrorMessage(null);
        card.setTranscript(null);
        card.setTranslatedText(null);
        card.setSummaryJson(null);
        return card;
    }

    /**
     * 작성자가 AI 결과(전사/번역/요약)를 직접 고친다. 보내지 않은 항목은 그대로 둔다.
     *
     * <p>처리가 끝난 카드만 고칠 수 있다. 진행 중인 카드를 고치면 뒤이어 도착한 파이프라인 결과가
     * 수정을 덮어써, 저장은 성공했는데 내용이 원래대로 돌아가 있는 상황이 된다.
     */
    @Transactional
    public HandoverCard updateResult(Long id, Member requester, UpdateHandoverResultRequest request) {
        HandoverCard card = getOwned(id, requester);
        if (card.getStatus() != HandoverStatus.COMPLETED) {
            throw new InvalidCardStateException("Only completed handover cards can be edited: " + id);
        }
        if (request.transcript() != null) {
            card.setTranscript(request.transcript().strip());
        }
        if (request.translatedText() != null) {
            card.setTranslatedText(request.translatedText().strip());
        }
        if (request.summary() != null) {
            card.setSummaryJson(writeJson(clean(request.summary())));
        }
        return card;
    }

    /** 화면의 빈 입력 칸이 빈 항목으로 저장되지 않도록 걸러내고 앞뒤 공백을 정리한다. */
    private SummaryDto clean(SummaryDto summary) {
        return new SummaryDto(cleanEntries(summary.keyPoints()), cleanEntries(summary.actionItems()),
                cleanEntries(summary.blockers()));
    }

    private List<SummaryEntryDto> cleanEntries(List<SummaryEntryDto> entries) {
        if (entries == null) {
            return List.of();
        }
        return entries.stream()
                .filter(entry -> entry != null && (StringUtils.hasText(entry.source()) || StringUtils.hasText(entry.target())))
                .map(entry -> new SummaryEntryDto(strip(entry.source()), strip(entry.target())))
                .toList();
    }

    private String strip(String value) {
        return value == null ? null : value.strip();
    }

    private HandoverCard getOwned(Long id, Member requester) {
        HandoverCard card = get(id);
        if (!card.getOwner().getId().equals(requester.getId())) {
            // 404, not 403 — avoids confirming to other users that this card ID exists
            throw new ResourceNotFoundException("Handover card not found: " + id);
        }
        return card;
    }

    @Transactional
    public void markTranscribing(Long id) {
        HandoverCard card = getForUpdate(id);
        card.setStatus(HandoverStatus.TRANSCRIBING);
    }

    @Transactional
    public void markTranscribed(Long id, String transcript, String translatedText) {
        HandoverCard card = getForUpdate(id);
        card.setTranscript(transcript);
        card.setTranslatedText(translatedText);
        card.setStatus(HandoverStatus.TRANSCRIBED);
    }

    @Transactional
    public void markSummarizing(Long id) {
        HandoverCard card = getForUpdate(id);
        card.setStatus(HandoverStatus.SUMMARIZING);
    }

    @Transactional
    public void markCompleted(Long id, SummaryResult summary) {
        HandoverCard card = getForUpdate(id);
        card.setSummaryJson(writeSummaryJson(summary));
        card.setStatus(HandoverStatus.COMPLETED);
    }

    @Transactional
    public void markFailed(Long id, String errorMessage) {
        HandoverCard card = getForUpdate(id);
        card.setStatus(HandoverStatus.FAILED);
        card.setErrorMessage(truncate(errorMessage));
    }

    private HandoverCard getForUpdate(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Handover card not found: " + id));
    }

    private String writeSummaryJson(SummaryResult summary) {
        return writeJson(summary);
    }

    private String writeJson(Object summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize summary", e);
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_ERROR_MESSAGE_LENGTH ? message.substring(0, MAX_ERROR_MESSAGE_LENGTH) : message;
    }
}
