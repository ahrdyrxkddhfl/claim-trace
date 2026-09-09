package com.claimtrace.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.claimtrace.domain.enums.DisclosureLevel;
import com.claimtrace.domain.enums.EvidenceSource;
import com.claimtrace.domain.enums.EvidenceStatus;
import com.claimtrace.domain.enums.Polarity;
import com.claimtrace.domain.enums.RejectionReasonType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판단 근거.
 *
 * <p>{@link ClaimItem} 과 {@link Rule} 의 교차 엔터티이면서 자기 속성을 갖는
 * 실체다(D-2). 근거를 항목에 JSON 배열로 넣거나 단순 조인 테이블로 두는
 * 대안을 기각한 이유는, 근거 하나하나가 채택·기각의 단위이고 상태와 사유를
 * 가져야 하기 때문이다. 근거가 조회와 갱신의 단위가 된다.
 *
 * <p>세 방향의 FK 가 모두 nullable 이고 {@code source} 값에 따라 하나만
 * 채워진다. 이 분기를 호출부에 맡기지 않기 위해 생성자를 열지 않고
 * {@link #fromRule}, {@link #fromAi}, {@link #manual} 세 정적 메서드만
 * 제공한다. 출처별 기본 공개 수준(D-4)도 이 세 메서드 안에서 결정된다.
 *
 * <p>기각된 근거는 삭제하지 않는다(D-3). RACI 참고사항의 "채택·미채택 사유
 * 기재는 의무사항"과 어긋나기도 하고, 이의제기 재검토 시 "무엇을 검토했고
 * 왜 배제했는가"가 필요하기 때문이다. 기각은 사유 유형과 함께 보존된다(INV-11).
 *
 * <p>{@code disclosureLevel} 이 INV-9 의 강제 수단이다. 고객 응답을 만들 때
 * 애플리케이션이 판단하는 것이 아니라, 근거 자체가 자기가 공개 가능한지를
 * 들고 있다.
 */
@Entity
@Table(
        name = "evidences",
        indexes = {
                @Index(name = "idx_evidences_item_status", columnList = "claim_item_id, status"),
                @Index(name = "idx_evidences_item_disclosure", columnList = "claim_item_id, disclosure_level"),
                @Index(name = "idx_evidences_rule_status", columnList = "rule_id, status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 근거가 속한 항목. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_item_id", nullable = false)
    private ClaimItem claimItem;

    /** 출처 룰. {@code source = RULE} 인 경우에만 채워지며 특정 버전을 가리킨다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private Rule rule;

    /** 출처 AI 권고. {@code source = AI} 인 경우에만 채워진다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_recommendation_id")
    private AiRecommendation aiRecommendation;

    /** 근거가 된 서류. 서류를 지목하지 않는 근거이면 {@code null}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    /** 생성 출처. 어느 FK 가 채워졌는지와 대응한다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvidenceSource source;

    /** 이 근거가 가리키는 방향. INV-5 가 세는 대상이다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Polarity polarity;

    /** 검토 상태. 기각되어도 삭제되지 않는다(D-3). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvidenceStatus status;

    /** 공개 수준. INV-9 의 강제 수단이다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DisclosureLevel disclosureLevel;

    /** 심사자에게 보이는 근거 문구. */
    @Column(nullable = false, columnDefinition = "text")
    private String contentInternal;

    /** 고객 설명문용 문구. 공개 수준이 INTERNAL 이면 {@code null} 일 수 있다. */
    @Column(columnDefinition = "text")
    private String contentCustomer;

    /** 새플리 기여도. {@code source = AI} 인 경우에만 값이 있다. */
    @Column(precision = 5, scale = 3)
    private BigDecimal contribution;

    /** 이 근거가 영향을 주는 금액(원). 산정과 무관한 근거이면 {@code null}. */
    private Integer targetAmount;

    /** 기각 사유 유형. 상태가 REJECTED 인 경우 반드시 존재한다(INV-11). */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private RejectionReasonType rejectionReasonType;

    /** 기각 상세 사유. 선택 항목이다. */
    @Column(columnDefinition = "text")
    private String rejectionNote;

    /** 채택·기각을 수행한 심사자. 미검토 상태이면 {@code null}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    /** 채택·기각 시각. 미검토 상태이면 {@code null}. */
    private LocalDateTime decidedAt;

    /** 근거 생성 시각. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 모든 필드를 받는 내부 생성자.
     *
     * <p>외부에 열지 않는다. 출처에 따라 채워야 할 FK 와 기본 공개 수준이
     * 달라서, 호출부가 그 조합을 매번 맞추게 하면 잘못된 상태의 근거가
     * 만들어질 수 있다.
     */
    private Evidence(ClaimItem claimItem, Rule rule, AiRecommendation aiRecommendation, Document document,
                     EvidenceSource source, Polarity polarity, DisclosureLevel disclosureLevel,
                     String contentInternal, String contentCustomer, BigDecimal contribution,
                     Integer targetAmount) {
        this.claimItem = claimItem;
        this.rule = rule;
        this.aiRecommendation = aiRecommendation;
        this.document = document;
        this.source = source;
        this.polarity = polarity;
        this.status = EvidenceStatus.GENERATED;
        this.disclosureLevel = disclosureLevel;
        this.contentInternal = contentInternal;
        this.contentCustomer = contentCustomer;
        this.contribution = contribution;
        this.targetAmount = targetAmount;
    }

    /**
     * 룰 매칭으로 근거를 생성한다.
     *
     * <p>극성, 문구, 공개 수준을 룰에서 물려받는다. 룰이 자기 버전의 문구를
     * 갖고 있으므로, 나중에 룰이 수정되어도 이미 만들어진 근거의 문구는
     * 변하지 않는다.
     *
     * @param claimItem 이 근거가 속한 항목
     * @param rule 매칭된 룰의 특정 버전
     * @param document 근거가 된 서류. 없으면 {@code null}
     * @param targetAmount 이 근거가 영향을 주는 금액(원). 없으면 {@code null}
     * @return 미검토 상태의 룰 근거
     */
    public static Evidence fromRule(ClaimItem claimItem, Rule rule, Document document, Integer targetAmount) {
        return new Evidence(
                claimItem, rule, null, document,
                EvidenceSource.RULE, rule.getPolarity(), rule.getDefaultDisclosureLevel(),
                rule.getContentInternal(), rule.getContentCustomer(), null, targetAmount);
    }

    /**
     * 모델 기여도로부터 근거를 생성한다.
     *
     * <p>공개 수준을 {@link DisclosureLevel#INTERNAL} 로 고정한다(D-4).
     * 새플리 기여도를 그대로 고객에게 내보내면 설명이 아니라 숫자 나열이 되고,
     * 심사자가 고객용 문구를 붙여 공개 수준을 올리는 것이 정상 경로다.
     *
     * @param claimItem 이 근거가 속한 항목
     * @param recommendation 이 근거를 낳은 AI 권고
     * @param polarity 기여도의 방향
     * @param contentInternal 심사자에게 보일 문구
     * @param contribution 새플리 기여도
     * @param targetAmount 이 근거가 영향을 주는 금액(원). 없으면 {@code null}
     * @return 미검토 상태의 AI 근거
     */
    public static Evidence fromAi(ClaimItem claimItem, AiRecommendation recommendation, Polarity polarity,
                                  String contentInternal, BigDecimal contribution, Integer targetAmount) {
        return new Evidence(
                claimItem, null, recommendation, null,
                EvidenceSource.AI, polarity, DisclosureLevel.INTERNAL,
                contentInternal, null, contribution, targetAmount);
    }

    /**
     * 심사자가 근거를 직접 추가한다.
     *
     * <p>사람이 판단해 넣은 것이므로 생성 즉시 {@link EvidenceStatus#ADOPTED} 다.
     * 자기가 추가한 근거를 다시 채택하게 하는 것은 의미 없는 절차다.
     * 공개 수준은 물려받을 출처가 없어 심사자가 직접 고른다.
     *
     * @param claimItem 이 근거가 속한 항목
     * @param polarity 근거의 방향
     * @param disclosureLevel 심사자가 고른 공개 수준
     * @param contentInternal 심사자용 문구
     * @param contentCustomer 고객용 문구. 공개 수준이 INTERNAL 이면 {@code null} 가능
     * @param document 근거가 된 서류. 없으면 {@code null}
     * @param targetAmount 이 근거가 영향을 주는 금액(원). 없으면 {@code null}
     * @param author 근거를 추가한 심사자
     * @param at 추가 시각
     * @return 채택 상태의 수동 근거
     */
    public static Evidence manual(ClaimItem claimItem, Polarity polarity, DisclosureLevel disclosureLevel,
                                  String contentInternal, String contentCustomer, Document document,
                                  Integer targetAmount, User author, LocalDateTime at) {
        Evidence evidence = new Evidence(
                claimItem, null, null, document,
                EvidenceSource.MANUAL, polarity, disclosureLevel,
                contentInternal, contentCustomer, null, targetAmount);
        evidence.status = EvidenceStatus.ADOPTED;
        evidence.decidedBy = author;
        evidence.decidedAt = at;
        return evidence;
    }

    /**
     * 근거를 채택한다.
     *
     * <p>이전에 기각된 근거를 다시 채택하는 경우 기각 사유를 지운다.
     * 상태가 ADOPTED 인데 기각 사유가 남아 있으면 모순된 데이터가 되고,
     * INV-11 은 "기각된 근거에는 사유가 있다"이지 "사유가 있으면 기각"이
     * 아니므로 지우는 쪽이 일관된다.
     *
     * @param reviewer 채택을 수행한 심사자
     * @param at 채택 시각
     */
    public void adopt(User reviewer, LocalDateTime at) {
        this.status = EvidenceStatus.ADOPTED;
        this.rejectionReasonType = null;
        this.rejectionNote = null;
        this.decidedBy = reviewer;
        this.decidedAt = at;
    }

    /**
     * 근거를 기각한다.
     *
     * <p>기각 사유 유형이 없으면 상태를 바꾸지 않고 예외를 던진다. INV-11 의
     * 마지막 방어선이며, 이 검사가 엔티티에 있는 이유는 상태 전이와 사유
     * 기록이 원자적으로 일어나야 하기 때문이다. 서비스가 검사를 빠뜨려도
     * 사유 없는 기각 레코드는 만들어지지 않는다.
     *
     * @param reasonType 기각 사유 유형
     * @param note 기각 상세 사유. 선택이며 없으면 {@code null}
     * @param reviewer 기각을 수행한 심사자
     * @param at 기각 시각
     * @throws IllegalArgumentException 기각 사유 유형이 {@code null} 인 경우
     */
    public void reject(RejectionReasonType reasonType, String note, User reviewer, LocalDateTime at) {
        if (reasonType == null) {
            throw new IllegalArgumentException("근거를 기각하려면 기각 사유 유형이 필요합니다 (INV-11)");
        }
        this.status = EvidenceStatus.REJECTED;
        this.rejectionReasonType = reasonType;
        this.rejectionNote = note;
        this.decidedBy = reviewer;
        this.decidedAt = at;
    }

    /**
     * 공개 수준을 변경한다.
     *
     * <p>화면 10 에서 심사자가 AI 근거에 고객용 문구를 붙여 공개로 올리거나,
     * 룰 근거를 내부용으로 내리는 경우에 쓴다.
     *
     * @param level 새 공개 수준
     */
    public void changeDisclosureLevel(DisclosureLevel level) {
        this.disclosureLevel = level;
    }

    /**
     * 고객 설명문의 재료가 될 수 있는 근거인지 판별한다.
     *
     * <p>설명문 초안 생성 서비스가 재료를 고를 때와 INV-5 의 부정 근거 개수를
     * 셀 때 같은 기준을 쓰도록 이 메서드로 모은다. 채택되었고, 공개 수준이
     * 고객용이며, 고객용 문구가 실제로 채워져 있어야 한다. 셋 중 하나라도
     * 빠지면 설명문에 빈 문장이 들어간다.
     *
     * @return 설명문에 쓸 수 있으면 {@code true}
     */
    public boolean isUsableInCustomerExplanation() {
        return this.status == EvidenceStatus.ADOPTED
                && this.disclosureLevel == DisclosureLevel.CUSTOMER
                && this.contentCustomer != null
                && !this.contentCustomer.isBlank();
    }
}
