package com.claimtrace.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.claimtrace.domain.enums.InterventionType;
import com.claimtrace.domain.enums.OverrideReasonType;

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
 * 인적 개입 이력.
 *
 * <p>세 유형이 서로 다른 경로로 만들어진다. {@code OVERRIDE} 는 판정 저장과
 * 같은 트랜잭션에서 <b>시스템이</b> 생성하고, {@code DUAL_CHECK} 와
 * {@code ESCALATION} 은 규칙 평가 결과로 요구된다.
 *
 * <p>오버라이드를 심사자가 선언하지 않는 것이 D-7 이다. 별도 엔드포인트로
 * 개입 사실을 기록하게 하면 선언하지 않는 우회가 가능하고, 판정 저장과
 * 개입 기록이 다른 트랜잭션이라 원자성도 깨진다. AI 권고와 다른 판정을
 * 저장하는 행위 자체가 개입이므로 시스템이 판별하지 않을 이유가 없다.
 *
 * <p>{@code interventionRule} 필드가 가리키는 것은 보험 계약({@link Policy})이
 * 아니라 개입 규칙({@link InterventionRule})이다. DBML 의 컬럼명이
 * {@code policy_id} 라 컬럼 매핑은 그대로 두되, 필드명은 혼동을 피해
 * 다르게 두었다. 같은 코드베이스에 {@code Claim.rule} 가 보험 계약으로
 * 존재하므로 이름이 겹치면 반드시 사고가 난다.
 *
 * <p>{@code approved} 는 승인 절차가 있는 유형에만 의미가 있다. 오버라이드는
 * 승인 대상이 아니라 이미 일어난 사실의 기록이므로 {@code null} 로 남는다.
 *
 * <p><b>승인 기록 세 컬럼은 설계 이후에 추가한 것이다.</b> 최초 DBML 에는
 * {@code approved} 불리언만 있었는데, 구현하면서 승인이라는 상태 전이의
 * 행위자가 어디에도 남지 않는다는 것을 발견했다. INV-1 은 모든 상태 전이에
 * 행위자를 요구하고 이 시스템의 전제가 "누가 무엇을 근거로 결정했는가"인데,
 * 직무 분리를 검사해 놓고 그 검사를 통과한 사람이 누구였는지 기록하지
 * 않으면 통제가 성립하지 않는다. {@code actorId} 는 개입을 <b>발생시킨</b>
 * 심사자이지 승인자가 아니다.
 */
@Entity
@Table(
        name = "interventions",
        indexes = {
                @Index(name = "idx_interventions_claim_type", columnList = "claim_id, type"),
                @Index(name = "idx_interventions_actor_time", columnList = "actor_id, occurred_at"),
                @Index(name = "idx_interventions_rule_time", columnList = "rule_id, occurred_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Intervention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 개입이 일어난 청구. 유형과 무관하게 항상 존재한다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    /** 개입 대상 항목. 오버라이드인 경우에만 채워진다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_item_id")
    private ClaimItem claimItem;

    /** 이 개입을 발생시킨 판정. 오버라이드인 경우에만 채워진다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id")
    private Review review;

    /** 뒤집힌 AI 권고. 어느 권고를 뒤집었는지를 지목한다(D-1). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_recommendation_id")
    private AiRecommendation aiRecommendation;

    /** 이 개입을 요구한 규칙. 규칙 발동이 아니면 {@code null}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private InterventionRule interventionRule;

    /** 개입 유형. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterventionType type;

    /** 오버라이드 사유 유형. 유형이 OVERRIDE 인 경우 반드시 존재한다(INV-3). */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private OverrideReasonType overrideReasonType;

    /** 개입 사유. 유형과 무관하게 필수다(INV-3, 보조수단성 ⑧). */
    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    /** 개입을 일으킨 행위자. INV-1 이 요구한다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    /** 승인 여부. 승인 절차가 없는 오버라이드에서는 {@code null} 로 남는다. */
    private Boolean approved;

    /** 승인·반려를 수행한 사용자. 처리 전이면 {@code null}. INV-1 이 요구하는 행위자다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    /** 승인·반려 시각. 처리 전이면 {@code null}. */
    private LocalDateTime approvedAt;

    /** 승인·반려 사유. 반려 시 기재를 권장한다. */
    @Column(columnDefinition = "text")
    private String approvalNote;

    /** 개입 발생 시각. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    /** 모든 필드를 받는 내부 생성자. 유형별 정적 메서드를 통해서만 호출된다. */
    private Intervention(Claim claim, ClaimItem claimItem, Review review,
                         AiRecommendation aiRecommendation, InterventionRule interventionRule,
                         InterventionType type, OverrideReasonType overrideReasonType,
                         String reason, User actor) {
        this.claim = claim;
        this.claimItem = claimItem;
        this.review = review;
        this.aiRecommendation = aiRecommendation;
        this.interventionRule = interventionRule;
        this.type = type;
        this.overrideReasonType = overrideReasonType;
        this.reason = reason;
        this.actor = actor;
    }

    /**
     * AI 권고를 뒤집은 개입을 기록한다.
     *
     * <p>판정 저장 서비스가 최신 권고와 판정이 다를 때 같은 트랜잭션에서
     * 호출한다(D-7). 사유 유형이나 사유 본문이 비어 있으면 예외를 던진다.
     * INV-3 이 요구하는 것은 "개입 기록이 생성된다"와 "사유가 있다" 둘
     * 다이므로, 기록만 만들어지고 사유가 비는 상태를 막는다.
     *
     * @param review 이 개입을 발생시킨 판정
     * @param recommendation 뒤집힌 AI 권고
     * @param reasonType 오버라이드 사유 유형
     * @param reason 오버라이드 상세 사유
     * @param actor 판정을 내린 심사자
     * @return 오버라이드 개입 기록
     * @throws IllegalArgumentException 사유 유형이 {@code null} 이거나 사유가 공백뿐인 경우
     */
    public static Intervention override(Review review, AiRecommendation recommendation,
                                        OverrideReasonType reasonType, String reason, User actor) {
        if (reasonType == null || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "AI 권고와 다른 판정에는 오버라이드 사유가 필요합니다 (INV-3)");
        }
        ClaimItem item = review.getClaimItem();
        return new Intervention(
                item.getClaim(), item, review, recommendation, null,
                InterventionType.OVERRIDE, reasonType, reason, actor);
    }

    /**
     * 규칙이 요구한 개입을 기록한다.
     *
     * <p>복수인 확인이나 차상위 검토처럼 승인 절차가 필요한 개입에 쓴다.
     * 생성 시점에는 승인 여부가 정해지지 않았으므로 {@code approved} 가
     * {@code null} 이고, 이 상태에서는 청구를 확정할 수 없다(INV-4).
     *
     * @param claim 개입이 요구된 청구
     * @param rule 개입을 요구한 규칙
     * @param reason 개입이 요구된 사유
     * @param actor 개입을 기록한 행위자
     * @return 승인 대기 상태의 개입 기록
     */
    public static Intervention required(Claim claim, InterventionRule rule, String reason, User actor) {
        return new Intervention(
                claim, null, null, null, rule,
                rule.getRequiredIntervention(), null, reason, actor);
    }

    /**
     * 개입을 승인하거나 반려한다.
     *
     * <p>화면 13 에서 심사관리자가 처리한다. 판정을 내린 심사자 본인은 승인
     * 권한에서 제외되는데(직무 분리, 보조수단성 ④), 그 검사는 승인 서비스가
     * 수행한다. 엔티티는 자기 필드만으로 판단할 수 없는 조건을 알지 못한다.
     *
     * <p>승인 여부와 행위자·시각을 함께 기록한다. 셋을 나누어 설정할 수
     * 있게 하면 승인 여부만 바뀌고 행위자가 비는 레코드가 만들어질 수 있다.
     *
     * @param approvedByManager 승인이면 {@code true}, 반려이면 {@code false}
     * @param approver 처리를 수행한 사용자
     * @param at 처리 시각
     * @param note 처리 사유. 없으면 {@code null}
     */
    public void resolve(boolean approvedByManager, User approver, LocalDateTime at, String note) {
        this.approved = approvedByManager;
        this.approvedBy = approver;
        this.approvedAt = at;
        this.approvalNote = note;
    }

    /**
     * 확정을 막고 있는 개입인지 판별한다.
     *
     * <p>판정 확정 서비스가 INV-4 를 검사할 때 쓴다. 승인 절차가 있는
     * 유형인데 아직 승인되지 않았다면 이 청구는 단독으로 확정될 수 없다.
     * 반려된 개입도 승인되지 않은 것이므로 확정을 막는다.
     *
     * @return 승인이 필요한 유형이면서 승인되지 않았으면 {@code true}
     */
    public boolean blocksDecision() {
        return this.type.requiresApproval() && !Boolean.TRUE.equals(this.approved);
    }
}
