package com.handovercard.summarization;

/**
 * 요약 항목 하나를 원문 언어와 번역 언어로 함께 담는다.
 * 두 언어를 별도 목록으로 두지 않고 항목 단위로 짝지어, 항목 수가 어긋나지 않도록 보장한다.
 */
public record SummaryEntry(
        String source,
        String target
) {
}
