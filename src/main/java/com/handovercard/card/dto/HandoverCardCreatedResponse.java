package com.handovercard.card.dto;

import com.handovercard.card.HandoverStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record HandoverCardCreatedResponse(
        @Schema(description = "생성/재처리된 카드 ID")
        Long id,

        @Schema(description = "현재 처리 상태")
        HandoverStatus status
) {
}
