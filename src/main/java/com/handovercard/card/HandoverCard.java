package com.handovercard.card;

import com.handovercard.common.BaseEntity;
import com.handovercard.member.Member;
import com.handovercard.team.Team;
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

    /**
     * 카드가 만들어질 당시 작성자가 속해 있던 팀. 이 팀의 팀원은 카드를 함께 볼 수 있다.
     * 현재 소속을 그때그때 따지지 않고 생성 시점을 기록해 두는 이유는, 나중에 팀에 합류한 사람이
     * 과거 인수인계 내용까지 소급해서 보게 되는 것을 막기 위해서다.
     */
    // 매퍼가 트랜잭션 밖(컨트롤러)에서 팀 이름을 읽으므로 지연 로딩하면 안 된다.
    // 같은 팀은 영속성 컨텍스트에서 재사용되어 목록 조회에서도 추가 쿼리가 팀 수만큼만 발생한다.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "team_id")
    private Team team;

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
