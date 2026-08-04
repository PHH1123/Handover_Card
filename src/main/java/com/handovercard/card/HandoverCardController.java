package com.handovercard.card;

import com.handovercard.card.dto.HandoverCardCreatedResponse;
import com.handovercard.card.dto.HandoverCardResponse;
import com.handovercard.card.dto.HandoverCardUploadRequest;
import com.handovercard.common.PageResponse;
import com.handovercard.pipeline.HandoverProcessingPipeline;
import com.handovercard.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
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

@Tag(name = "인수인계 카드", description = "음성 업로드부터 조회/삭제/재처리까지 인수인계 카드 관리")
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

    @Operation(summary = "인수인계 카드 생성", description = "음성 파일을 업로드하면 202 Accepted로 즉시 응답하고, "
            + "백그라운드에서 STT → 번역 → 요약 파이프라인이 비동기로 실행됩니다. "
            + "이후 상태는 조회 API의 status 필드로 확인합니다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<HandoverCardCreatedResponse> create(@Valid @ParameterObject @ModelAttribute HandoverCardUploadRequest request,
                                                                @AuthenticationPrincipal CustomUserDetails principal) {
        HandoverCard card = handoverCardService.createAndPersist(request, principal.getMember());
        processingPipeline.processAsync(card.getId());

        HandoverCardCreatedResponse body = new HandoverCardCreatedResponse(card.getId(), card.getStatus());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/handover-cards/" + card.getId()))
                .body(body);
    }

    @Operation(summary = "인수인계 카드 단건 조회", description = "카드를 만든 소유자 또는 receiverEmail로 연결된 수신자만 조회할 수 있습니다. "
            + "권한이 없으면 카드 존재 여부를 숨기기 위해 403이 아닌 404를 반환합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<HandoverCardResponse> get(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        HandoverCard card = handoverCardService.getAccessible(id, principal.getMember());
        return ResponseEntity.ok(handoverCardMapper.toResponse(card));
    }

    @Operation(summary = "내가 접근 가능한 카드 목록 조회", description = "내가 소유자이거나 수신자로 연결된 카드를 최신순으로 페이지네이션하여 반환합니다.")
    @GetMapping
    public ResponseEntity<PageResponse<HandoverCardResponse>> list(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "페이지 번호(0부터 시작), 크기(기본 20, 최대 100), 정렬 기준을 지정합니다.")
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<HandoverCardResponse> cards = handoverCardService.listAccessible(principal.getMember(), pageable)
                .map(handoverCardMapper::toResponse);
        return ResponseEntity.ok(PageResponse.from(cards));
    }

    @Operation(summary = "인수인계 카드 삭제", description = "소유자만 삭제할 수 있습니다. 업로드된 오디오 파일도 함께 삭제됩니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        handoverCardService.delete(id, principal.getMember());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "실패한 카드 재처리", description = "소유자가 FAILED 상태인 카드를 다시 처리 파이프라인에 태웁니다. "
            + "FAILED가 아닌 카드를 재처리하려 하면 409를 반환합니다.")
    @PostMapping("/{id}/reprocess")
    public ResponseEntity<HandoverCardCreatedResponse> reprocess(@PathVariable Long id,
                                                                   @AuthenticationPrincipal CustomUserDetails principal) {
        HandoverCard card = handoverCardService.reprocess(id, principal.getMember());
        processingPipeline.processAsync(card.getId());

        HandoverCardCreatedResponse body = new HandoverCardCreatedResponse(card.getId(), card.getStatus());
        return ResponseEntity.accepted().body(body);
    }
}
