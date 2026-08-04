package com.handovercard.card.dto;

import com.handovercard.card.HandoverStatus;

import java.time.Instant;

public record HandoverCardResponse(
        Long id,
        String senderName,
        String receiverName,
        String sourceLanguage,
        String targetLanguage,
        HandoverStatus status,
        String transcript,
        String translatedText,
        SummaryDto summary,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
