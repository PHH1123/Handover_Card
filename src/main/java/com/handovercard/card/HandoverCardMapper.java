package com.handovercard.card;

import tools.jackson.databind.ObjectMapper;
import com.handovercard.card.dto.HandoverCardResponse;
import com.handovercard.card.dto.SummaryDto;
import org.springframework.stereotype.Component;

@Component
public class HandoverCardMapper {

    private final ObjectMapper objectMapper;

    public HandoverCardMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public HandoverCardResponse toResponse(HandoverCard card) {
        return new HandoverCardResponse(
                card.getId(),
                card.getSenderName(),
                card.getReceiverName(),
                card.getTeam() != null ? card.getTeam().getName() : null,
                card.getSourceLanguage(),
                card.getTargetLanguage(),
                card.getStatus(),
                card.getTranscript(),
                card.getTranslatedText(),
                toSummaryDto(card.getSummaryJson()),
                card.getErrorMessage(),
                card.getCreatedAt(),
                card.getUpdatedAt()
        );
    }

    private SummaryDto toSummaryDto(String summaryJson) {
        if (summaryJson == null) {
            return null;
        }
        try {
            return objectMapper.readValue(summaryJson, SummaryDto.class);
        } catch (Exception e) {
            return null;
        }
    }
}
