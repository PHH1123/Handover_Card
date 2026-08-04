package com.handovercard.summarization;

public record SummarizationRequest(
        String transcript,
        String translatedText,
        String sourceLanguage,
        String targetLanguage
) {
}
