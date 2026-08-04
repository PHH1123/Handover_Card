package com.handovercard.summarization;

import java.util.List;

public record SummaryResult(
        List<String> keyPoints,
        List<String> actionItems,
        List<String> blockers
) {
}
