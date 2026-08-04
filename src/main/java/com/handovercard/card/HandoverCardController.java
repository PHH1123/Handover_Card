package com.handovercard.card;

import com.handovercard.card.dto.HandoverCardCreatedResponse;
import com.handovercard.card.dto.HandoverCardResponse;
import com.handovercard.card.dto.HandoverCardUploadRequest;
import com.handovercard.common.PageResponse;
import com.handovercard.pipeline.HandoverProcessingPipeline;
import com.handovercard.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    public ResponseEntity<HandoverCardCreatedResponse> create(@Valid @ModelAttribute HandoverCardUploadRequest request,
                                                                @AuthenticationPrincipal CustomUserDetails principal) {
        HandoverCard card = handoverCardService.createAndPersist(request, principal.getMember());
        processingPipeline.processAsync(card.getId());

        HandoverCardCreatedResponse body = new HandoverCardCreatedResponse(card.getId(), card.getStatus());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/handover-cards/" + card.getId()))
                .body(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<HandoverCardResponse> get(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        HandoverCard card = handoverCardService.getAccessible(id, principal.getMember());
        return ResponseEntity.ok(handoverCardMapper.toResponse(card));
    }

    @GetMapping
    public ResponseEntity<PageResponse<HandoverCardResponse>> list(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<HandoverCardResponse> cards = handoverCardService.listAccessible(principal.getMember(), pageable)
                .map(handoverCardMapper::toResponse);
        return ResponseEntity.ok(PageResponse.from(cards));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        handoverCardService.delete(id, principal.getMember());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reprocess")
    public ResponseEntity<HandoverCardCreatedResponse> reprocess(@PathVariable Long id,
                                                                   @AuthenticationPrincipal CustomUserDetails principal) {
        HandoverCard card = handoverCardService.reprocess(id, principal.getMember());
        processingPipeline.processAsync(card.getId());

        HandoverCardCreatedResponse body = new HandoverCardCreatedResponse(card.getId(), card.getStatus());
        return ResponseEntity.accepted().body(body);
    }
}
