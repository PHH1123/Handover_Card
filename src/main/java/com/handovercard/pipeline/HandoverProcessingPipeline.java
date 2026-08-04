package com.handovercard.pipeline;

import com.handovercard.card.HandoverCard;
import com.handovercard.card.HandoverCardService;
import com.handovercard.storage.AudioStorageService;
import com.handovercard.summarization.SummarizationRequest;
import com.handovercard.summarization.SummarizationService;
import com.handovercard.summarization.SummaryResult;
import com.handovercard.transcription.TranscriptionRequest;
import com.handovercard.transcription.TranscriptionResult;
import com.handovercard.transcription.TranscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class HandoverProcessingPipeline {

    private static final Logger log = LoggerFactory.getLogger(HandoverProcessingPipeline.class);

    private final HandoverCardService handoverCardService;
    private final AudioStorageService audioStorageService;
    private final TranscriptionService transcriptionService;
    private final SummarizationService summarizationService;

    public HandoverProcessingPipeline(HandoverCardService handoverCardService, AudioStorageService audioStorageService,
                                       TranscriptionService transcriptionService, SummarizationService summarizationService) {
        this.handoverCardService = handoverCardService;
        this.audioStorageService = audioStorageService;
        this.transcriptionService = transcriptionService;
        this.summarizationService = summarizationService;
    }

    @Async("handoverTaskExecutor")
    public void processAsync(Long cardId) {
        try {
            HandoverCard card = handoverCardService.get(cardId);

            handoverCardService.markTranscribing(cardId);
            TranscriptionRequest transcriptionRequest = new TranscriptionRequest(
                    cardId,
                    audioStorageService.resolve(card.getAudioFilePath()),
                    card.getSourceLanguage(),
                    card.getTargetLanguage()
            );
            TranscriptionResult transcriptionResult = transcriptionService.transcribeAndTranslate(transcriptionRequest);
            handoverCardService.markTranscribed(cardId, transcriptionResult.transcript(), transcriptionResult.translatedText());

            handoverCardService.markSummarizing(cardId);
            SummarizationRequest summarizationRequest = new SummarizationRequest(
                    transcriptionResult.transcript(),
                    transcriptionResult.translatedText(),
                    card.getSourceLanguage(),
                    card.getTargetLanguage()
            );
            SummaryResult summaryResult = summarizationService.summarize(summarizationRequest);
            handoverCardService.markCompleted(cardId, summaryResult);
        } catch (Exception ex) {
            log.error("Handover processing pipeline failed for card {}", cardId, ex);
            handoverCardService.markFailed(cardId, describeError(ex));
        }
    }

    private String describeError(Exception ex) {
        String message = ex.getMessage();
        return message != null ? ex.getClass().getSimpleName() + ": " + message : ex.getClass().getSimpleName();
    }
}
