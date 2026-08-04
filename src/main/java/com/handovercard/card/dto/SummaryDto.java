package com.handovercard.card.dto;

import java.util.List;

public record SummaryDto(
        List<String> keyPoints,
        List<String> actionItems,
        List<String> blockers
) {
}
