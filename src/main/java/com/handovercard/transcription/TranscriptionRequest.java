package com.handovercard.transcription;

import java.nio.file.Path;

public record TranscriptionRequest(
        Long cardId,
        Path audioFilePath,
        String sourceLanguage,
        String targetLanguage
) {
}
