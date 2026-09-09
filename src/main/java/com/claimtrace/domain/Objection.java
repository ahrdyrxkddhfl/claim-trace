package com.claimtrace.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.claimtrace.domain.enums.ObjectionStatus;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이의제기.
 *
 * <p>재검토 결과는 이 엔티티가 아니라 {@link Review} 에 새 레코드로 저장되고,
 * 최초 판정은 이력으로 보존된다(D-6). 이의제기는 재검토를 촉발한 사건과
 * 그 회신을 담을 뿐 판정을 담지 않는다. 판정이 있어야 할 곳은 언제나
 * {@code reviews} 한 곳이다.
 *
 * <p>{@code targetItems} 는 대상 청구 항목 식별자 배열의 JSON 문자열이다.
 * 청구 항목과의 다대다 관계를 별도 테이블로 두지 않은 것은, 이 값이 조회의
 * 단위가 아니라 이의제기 한 건에 딸린 속성이기 때문이다. 항목별로 이의제기를
 * 집계하는 화면이 없어 조인 테이블이 얻는 것이 없다.
 *
 * <p>재검토 심사자는 최초 판정자와 달라야 한다. 이 제약은 DB 로 표현할 수
 * 없어 재검토 배정 서비스가 검사한다. 직무 분리 원칙의 연장이며, 자기가
 * 내린 판정을 자기가 다시 보는 것은 재검토가 아니다.
 */
@Entity
@Table(
        name = "objections",
        indexes = @Index(name = "idx_objections_claim_status", columnList = "claim_id, status")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Objection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이의제기 대상 청구. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    /** 대상 항목 식별자 배열의 JSON 문자열. 예: [1203, 1204] */
    @Column(nullable = false, columnDefinition = "text")
    private String targetItems;

    /** 청구인이 작성한 이의 사유. */
    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    /** 처리 상태. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ObjectionStatus status;

    /** 재검토 심사자. 최초 판정자와 달라야 한다. 배정 전이면 {@code null}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private User reviewer;

    /** 재검토 결과 회신. 회신 전이면 {@code null}. */
    @Column(columnDefinition = "text")
    private String response;

    /** 접수 시각. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    /** 회신 시각. 회신 전이면 {@code null}. */
    private LocalDateTime answeredAt;

    /**
     * 이의제기를 생성한다.
     *
     * <p>새 이의제기는 언제나 {@link ObjectionStatus#RECEIVED} 이므로 상태를
     * 생성자에서 받지 않는다.
     *
     * @param claim 대상 청구
     * @param targetItems 대상 항목 식별자 배열의 JSON 문자열
     * @param reason 청구인이 작성한 이의 사유
     */
    @Builder
    private Objection(Claim claim, String targetItems, String reason) {
        this.claim = claim;
        this.targetItems = targetItems;
        this.reason = reason;
        this.status = ObjectionStatus.RECEIVED;
    }

    /**
     * 재검토 심사자를 배정하고 재검토중 상태로 전이시킨다.
     *
     * <p>최초 판정자와 다른 사람인지에 대한 검사는 호출하는 서비스가
     * 수행한다. 이의제기 엔티티는 최초 판정이 누구의 것이었는지 알지 못한다.
     *
     * @param assignedReviewer 재검토를 맡을 심사자
     */
    public void assignReviewer(User assignedReviewer) {
        this.reviewer = assignedReviewer;
        this.status = ObjectionStatus.REVIEWING;
    }

    /**
     * 재검토 결과를 회신하고 회신 완료 상태로 전이시킨다.
     *
     * @param answer 재검토 결과 회신 내용
     * @param at 회신 시각
     */
    public void answer(String answer, LocalDateTime at) {
        this.response = answer;
        this.status = ObjectionStatus.ANSWERED;
        this.answeredAt = at;
    }
}
