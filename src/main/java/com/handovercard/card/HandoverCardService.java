package com.handovercard.card;

import tools.jackson.databind.ObjectMapper;
import com.handovercard.card.dto.HandoverCardUploadRequest;
import com.handovercard.common.ResourceNotFoundException;
import com.handovercard.storage.AudioStorageService;
import com.handovercard.storage.StoredAudio;
import com.handovercard.summarization.SummaryResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HandoverCardService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    private final HandoverCardRepository repository;
    private final AudioStorageService audioStorageService;
    private final ObjectMapper objectMapper;

    public HandoverCardService(HandoverCardRepository repository, AudioStorageService audioStorageService,
                                ObjectMapper objectMapper) {
        this.repository = repository;
        this.audioStorageService = audioStorageService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public HandoverCard createAndPersist(HandoverCardUploadRequest request) {
        HandoverCard card = new HandoverCard(
                request.getSenderName(),
                request.getReceiverName(),
                request.getSourceLanguage(),
                request.getTargetLanguage(),
                null, null, null, null
        );
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
