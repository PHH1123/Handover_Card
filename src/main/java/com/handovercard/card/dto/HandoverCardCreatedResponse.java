package com.handovercard.card.dto;

import com.handovercard.card.HandoverStatus;

public record HandoverCardCreatedResponse(
        Long id,
        HandoverStatus status
) {
}
