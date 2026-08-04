package com.handovercard.card;

import com.handovercard.card.dto.HandoverCardCreatedResponse;
import com.handovercard.card.dto.HandoverCardResponse;
import com.handovercard.card.dto.HandoverCardUploadRequest;
import com.handovercard.pipeline.HandoverProcessingPipeline;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/handover-cards")
public class HandoverCardController {

    private final HandoverCardService handoverCardService;
    private final HandoverCardMapper handoverCardMapper;
    private final HandoverProcessingPipeline processingPipeline;

    public HandoverCardController(HandoverCardService handoverCardService, HandoverCardMapper handoverCardMapper,
                                   HandoverProcessingPipeline processingPipeline) {
        this.handoverCardService = handoverCardService;
        this.handoverCardMapper = handoverCardMapper;
        this.processingPipeline = processingPipeline;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<HandoverCardCreatedResponse> create(@Valid @ModelAttribute HandoverCardUploadRequest request) {
        HandoverCard card = handoverCardService.createAndPersist(request);
        processingPipeline.processAsync(card.getId());

        HandoverCardCreatedResponse body = new HandoverCardCreatedResponse(card.getId(), card.getStatus());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/handover-cards/" + card.getId()))
                .body(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<HandoverCardResponse> get(@PathVariable Long id) {
        HandoverCard card = handoverCardService.get(id);
        return ResponseEntity.ok(handoverCardMapper.toResponse(card));
    }
}
