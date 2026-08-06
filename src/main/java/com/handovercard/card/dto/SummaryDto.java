package com.handovercard.card.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record SummaryDto(
        @Schema(description = "핵심 내용 목록 (항목마다 원문/번역 언어 쌍)")
        List<SummaryEntryDto> keyPoints,

        @Schema(description = "해야 할 일 목록 (항목마다 원문/번역 언어 쌍)")
        List<SummaryEntryDto> actionItems,

        @Schema(description = "막힌 부분/이슈 목록 (항목마다 원문/번역 언어 쌍)")
        List<SummaryEntryDto> blockers
) {
}
