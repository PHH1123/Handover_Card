package com.handovercard.card.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record SummaryDto(
        @Schema(description = "핵심 내용 목록")
        List<String> keyPoints,

        @Schema(description = "해야 할 일 목록")
        List<String> actionItems,

        @Schema(description = "막힌 부분/이슈 목록")
        List<String> blockers
) {
}
