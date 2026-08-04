package com.handovercard.summarization.mock;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "summarization.mock")
public record MockSummarizationProperties(
        long simulatedDelayMs
) {
}
