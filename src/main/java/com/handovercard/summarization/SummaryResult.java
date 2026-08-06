package com.handovercard.summarization;

import java.util.List;

public record SummaryResult(
        List<SummaryEntry> keyPoints,
        List<SummaryEntry> actionItems,
        List<SummaryEntry> blockers
) {
}
