package com.handovercard.card.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class HandoverCardUploadRequest {

    @NotNull(message = "audio file is required")
    private MultipartFile audio;

    @NotBlank
    private String senderName;

    @NotBlank
    private String receiverName;

    // optional: if it matches a registered member, that member also gets read access to the card
    @Email
    private String receiverEmail;

    @NotBlank
    private String sourceLanguage;

    @NotBlank
    private String targetLanguage;
}
