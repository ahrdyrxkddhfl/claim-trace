package com.claimtrace.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.claimtrace.domain.enums.ExplanationStatus;
import com.claimtrace.domain.enums.ExplanationType;

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
 * 설명서. 전역 설명과 국소 설명 두 유형이 한 테이블에 들어간다.
 *
 * <p>유형에 따라 채워지는 컬럼이 다르다. 전역 설명은 모델 단위로 사전
 * 생성되므로 {@code modelName} 이 있고 {@code claim} 이 없다. 국소 설명은
 * 청구별 신청에 응답하므로 {@code claim} 이 있고 {@code dueDate} 가 필수다(INV-8).
 * 이 분기를 호출부에 맡기지 않기 위해 {@link #global} 과 {@link #local}
 * 두 정적 메서드만 제공한다.
 *
 * <p>초안은 자동 생성되지만 발급은 자동이 아니다. {@code GENERATING} 에서
 * {@code PROVIDED} 로 가려면 심사자가 검토하고 확정해야 한다. 완전 자동
 * 발급 경로를 두지 않은 것은 보조수단성 원칙이 요구하는 사람의 최종
 * 확인이 빠지기 때문이다.
 *
 * <p>INV-5 는 이 엔티티가 아니라 초안 생성 서비스가 검사한다. 부지급·일부지급
 * 항목에 고객용 부정 근거가 1건 이상 있는지는 항목과 근거를 함께 조회해야
 * 알 수 있고, 설명서 자기 자신의 필드만으로는 판단할 수 없다.
 */
@Entity
@Table(
        name = "explanations",
        indexes = @Index(name = "idx_explanations_claim_type", columnList = "claim_id, type")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Explanation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 대상 청구. 국소 설명인 경우에만 채워진다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id")
    private Claim claim;

    /** 설명 범위. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExplanationType type;

    /** 처리 상태. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExplanationStatus status;

    /** 대상 모델. 전역 설명인 경우에만 채워진다. */
    @Column(length = 50)
    private String modelName;

    /** 설명문 본문. 초안 생성 후 심사자가 수정·확정한다. */
    @Column(columnDefinition = "text")
    private String body;

    /** 발급 파일 주소. 발급 전이면 {@code null}. */
    @Column(length = 500)
    private String fileUrl;

    /** 초안을 확정한 심사자. 확정 전이면 {@code null}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drafted_by")
    private User draftedBy;

    /** 신청 시각. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    /** 제공 기한. 국소 설명이면 반드시 존재한다(INV-8). */
    private LocalDate dueDate;

    /** 발급 시각. 발급 전이면 {@code null}. */
    private LocalDateTime providedAt;

    /** 모든 필드를 받는 내부 생성자. 유형별 정적 메서드를 통해서만 호출된다. */
    private Explanation(Claim claim, ExplanationType type, String modelName, LocalDate dueDate) {
        this.claim = claim;
        this.type = type;
        this.status = ExplanationStatus.REQUESTED;
        this.modelName = modelName;
        this.dueDate = dueDate;
    }

    /**
     * 모델 단위의 전역 설명서를 생성한다.
     *
     * @param modelName 대상 모델 이름
     * @return 신청 접수 상태의 전역 설명서
     */
    public static Explanation global(String modelName) {
        return new Explanation(null, ExplanationType.GLOBAL, modelName, null);
    }

    /**
     * 청구 단위의 국소 설명서를 생성한다.
     *
     * <p>제공 기한이 없으면 예외를 던진다. INV-8 은 조건부 NOT NULL 이라
     * DB 제약만으로 표현되지 않으므로 생성 시점에 막는다.
     *
     * @param claim 대상 청구
     * @param dueDate 제공 기한
     * @return 신청 접수 상태의 국소 설명서
     * @throws IllegalArgumentException 제공 기한이 {@code null} 인 경우
     */
    public static Explanation local(Claim claim, LocalDate dueDate) {
        if (dueDate == null) {
            throw new IllegalArgumentException("국소 설명서에는 제공 기한이 필요합니다 (INV-8)");
        }
        return new Explanation(claim, ExplanationType.LOCAL, null, dueDate);
    }

    /**
     * 초안 본문을 채우고 작성중 상태로 전이시킨다.
     *
     * @param draftBody 생성된 초안 본문
     */
    public void applyDraft(String draftBody) {
        this.body = draftBody;
        this.status = ExplanationStatus.GENERATING;
    }

    /**
     * 심사자가 설명서를 확정해 발급한다.
     *
     * <p>본문이 비어 있으면 예외를 던진다. 빈 설명서가 고객에게 나가는 것은
     * 설명서를 발급하지 않은 것보다 나쁘다.
     *
     * @param finalBody 심사자가 확정한 본문
     * @param reviewer 확정한 심사자
     * @param at 발급 시각
     * @throws IllegalArgumentException 본문이 {@code null} 이거나 공백뿐인 경우
     */
    public void provide(String finalBody, User reviewer, LocalDateTime at) {
        if (finalBody == null || finalBody.isBlank()) {
            throw new IllegalArgumentException("본문이 비어 있는 설명서는 발급할 수 없습니다");
        }
        this.body = finalBody;
        this.draftedBy = reviewer;
        this.status = ExplanationStatus.PROVIDED;
        this.providedAt = at;
    }
}
