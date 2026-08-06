package com.handovercard.card.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SummaryEntryDto(
        @Schema(description = "원본 음성 언어로 작성된 요약 항목")
        String source,

        @Schema(description = "번역 대상 언어로 작성된 같은 내용의 요약 항목")
        String target
) {
}
