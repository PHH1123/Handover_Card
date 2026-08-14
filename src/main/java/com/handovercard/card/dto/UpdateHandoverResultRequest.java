package com.handovercard.card.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

/**
 * 작성자가 AI 결과를 손볼 때 보내는 값. 세 항목 모두 선택이며, 보내지 않은(null) 항목은 그대로 둔다.
 *
 * <p>내용을 지우는 수정은 받지 않는다. 전사나 번역이 빈 칸이 되면 화면에서는 "아직 결과가 없습니다"와
 * 구분되지 않아, 고친 것인지 처리가 덜 된 것인지 알 수 없게 된다.
 */
public record UpdateHandoverResultRequest(

        @Schema(description = "고쳐 쓸 전사 텍스트. 생략하면 기존 전사를 그대로 둡니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "빈 값으로 지울 수 없습니다. 바꾸지 않으려면 항목을 생략하세요.")
        String transcript,

        @Schema(description = "고쳐 쓸 번역 텍스트. 생략하면 기존 번역을 그대로 둡니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "빈 값으로 지울 수 없습니다. 바꾸지 않으려면 항목을 생략하세요.")
        String translatedText,

        @Schema(description = "고쳐 쓸 요약 전체. 보내면 기존 요약을 통째로 대체합니다. "
                + "원문/번역이 모두 빈 항목은 저장하지 않으므로, 항목을 지우려면 빈 값으로 보내면 됩니다.")
        @Valid
        SummaryDto summary
) {

    /** 셋 다 없으면 바꿀 것이 없다. 아무 일도 일어나지 않은 요청이 200으로 성공하는 편이 더 헷갈린다. */
    @Schema(hidden = true)
    @AssertTrue(message = "transcript, translatedText, summary 중 최소 하나는 보내야 합니다")
    public boolean isAnyFieldPresent() {
        return transcript != null || translatedText != null || summary != null;
    }
}
