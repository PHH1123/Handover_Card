package com.handovercard.transcription.mock;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "transcription.mock")
public record MockTranscriptionProperties(
        long simulatedDelayMs
) {
}
