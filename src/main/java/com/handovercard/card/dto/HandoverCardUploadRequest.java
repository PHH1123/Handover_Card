package com.handovercard.card.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class HandoverCardUploadRequest {

    @Schema(description = "인수인계 음성 파일 (mp3/wav/m4a/ogg/aac)")
    @NotNull(message = "audio file is required")
    private MultipartFile audio;

    @Schema(description = "인수인계자(발신자) 이름", example = "Alex")
    @NotBlank
    private String senderName;

    @Schema(description = "인수받는 사람(수신자) 이름", example = "Minji")
    @NotBlank
    private String receiverName;

    @Schema(description = "수신자 이메일 (선택). 가입된 회원 이메일과 일치하면 해당 회원도 이 카드를 조회할 수 있습니다.")
    @Email
    private String receiverEmail;

    @Schema(description = "원본 음성의 언어 코드", example = "en")
    @NotBlank
    private String sourceLanguage;

    @Schema(description = "번역 대상 언어 코드", example = "ko")
    @NotBlank
    private String targetLanguage;
}
