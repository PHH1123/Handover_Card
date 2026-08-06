package com.handovercard.transcription;

import org.springframework.core.io.Resource;

public record TranscriptionRequest(
        Long cardId,
        Resource audio,
        String sourceLanguage,
        String targetLanguage
) {
}
