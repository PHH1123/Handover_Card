package com.handovercard.card;

import com.handovercard.common.BaseEntity;
import com.handovercard.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "handover_cards")
@Getter
@Setter
@NoArgsConstructor
public class HandoverCard extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private Member owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id")
    private Member receiver;

    @Column(nullable = false)
    private String senderName;

    @Column(nullable = false)
    private String receiverName;

    @Column(nullable = false)
    private String sourceLanguage;

    @Column(nullable = false)
    private String targetLanguage;

    private String audioFilePath;

    private String originalFilename;

    private String contentType;

    private Long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HandoverStatus status;

    @Column(columnDefinition = "TEXT")
    private String transcript;

    @Column(columnDefinition = "TEXT")
    private String translatedText;

    @Column(columnDefinition = "TEXT")
    private String summaryJson;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    public HandoverCard(Member owner, String senderName, String receiverName, String sourceLanguage, String targetLanguage,
                         String audioFilePath, String originalFilename, String contentType, Long fileSizeBytes) {
        this.owner = owner;
        this.senderName = senderName;
        this.receiverName = receiverName;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.audioFilePath = audioFilePath;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.status = HandoverStatus.RECEIVED;
    }
}
