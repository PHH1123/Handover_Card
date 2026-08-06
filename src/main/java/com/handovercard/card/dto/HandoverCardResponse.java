package com.handovercard.card.dto;

import com.handovercard.card.HandoverStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record HandoverCardResponse(
        @Schema(description = "카드 ID")
        Long id,

        @Schema(description = "발신자 이름")
        String senderName,

        @Schema(description = "수신자 이름")
        String receiverName,

        @Schema(description = "카드가 만들어질 당시 작성자의 소속 팀 이름. 이 팀의 팀원은 카드를 함께 볼 수 있습니다. 팀 없이 만들었으면 null")
        String teamName,

        @Schema(description = "원본 음성 언어 코드")
        String sourceLanguage,

        @Schema(description = "번역 대상 언어 코드")
        String targetLanguage,

        @Schema(description = "처리 상태 (RECEIVED → TRANSCRIBING → TRANSCRIBED → SUMMARIZING → COMPLETED, 또는 FAILED)")
        HandoverStatus status,

        @Schema(description = "STT로 변환된 원문 텍스트")
        String transcript,

        @Schema(description = "번역된 텍스트")
        String translatedText,

        @Schema(description = "요약 결과 (핵심 내용/할 일/이슈)")
        SummaryDto summary,

        @Schema(description = "FAILED 상태일 때의 오류 메시지")
        String errorMessage,

        @Schema(description = "생성 일시")
        Instant createdAt,

        @Schema(description = "마지막 수정 일시")
        Instant updatedAt
) {
}
