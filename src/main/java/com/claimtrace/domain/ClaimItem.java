package com.claimtrace.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 청구 항목. 판정의 단위.
 *
 * <p>이 시스템의 중심에 있는 엔티티다. AI 권고({@code ai_recommendations}),
 * 근거({@code evidences}), 사람의 판정({@code reviews})이 모두 항목 단위로
 * 매달린다. 기술서 Pain 1 이 지적한 "판정 사유를 항목 단위로 확인할 수 없다"에
 * 대한 구조적 답이 여기다.
 *
 * <p>항목은 OCR 결과로부터 시스템이 생성하고, 배당된 건은 심사자가 화면 9 에서
 * 확인·보정한다(D-8). 청구인이 직접 입력하지 않는 이유는 의료비 세부 항목
 * 분류를 청구인에게 요구하면 청구 자체의 진입장벽이 높아지기 때문이다.
 *
 * <p>{@code sourceDocument} 는 이 항목이 어느 서류에서 추출되었는지를 가리킨다.
 * 심사자가 화면 9 에서 직접 추가한 항목이면 {@code null} 이다.
 *
 * <p>{@code (claimId, seq)} 에 유니크 제약을 둔다. 같은 청구 안에서 순번이
 * 겹치면 화면 9 좌측 항목 목록의 순서가 흔들리고, 부지급 설명문에서
 * "2번 항목"이 어느 것인지 특정되지 않는다.
 */
@Entity
@Table(
        name = "claim_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_claim_items_claim_seq",
                columnNames = {"claim_id", "seq"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClaimItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 항목이 속한 청구. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    /** 적용 담보. 한도와 자기부담률의 출처이며 룰 매칭의 기준이다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coverage_id", nullable = false)
    private Coverage coverage;

    /** 청구 내 항목 순번. 화면 표시 순서이자 항목을 지칭하는 번호다. */
    @Column(nullable = false)
    private Short seq;

    /** 진료 항목명. 예: 도수치료 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 행위 코드. 서류에서 추출되지 않으면 {@code null}. */
    @Column(length = 20)
    private String procedureCode;

    /** 급여 여부. {@code true} 면 급여, {@code false} 면 비급여다. */
    @Column(nullable = false)
    private boolean isCovered;

    /** 시행 회차. */
    @Column(nullable = false)
    private Short quantity;

    /** 청구금액(원). 개입 규칙의 발동 조건 필드이기도 하다. */
    @Column(nullable = false)
    private Integer claimedAmount;

    /** OCR 추출 원본 서류. 심사자가 직접 추가한 항목이면 {@code null}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id")
    private Document sourceDocument;

    /** 항목 생성 시각. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 청구 항목을 생성한다.
     *
     * @param claim 이 항목이 속한 청구
     * @param coverage 적용 담보
     * @param seq 청구 내 항목 순번
     * @param name 진료 항목명
     * @param procedureCode 행위 코드. 없으면 {@code null}
     * @param isCovered 급여 여부
     * @param quantity 시행 회차
     * @param claimedAmount 청구금액(원)
     * @param sourceDocument OCR 추출 원본 서류. 없으면 {@code null}
     */
    @Builder
    private ClaimItem(Claim claim, Coverage coverage, Short seq, String name, String procedureCode,
                      boolean isCovered, Short quantity, Integer claimedAmount, Document sourceDocument) {
        this.claim = claim;
        this.coverage = coverage;
        this.seq = seq;
        this.name = name;
        this.procedureCode = procedureCode;
        this.isCovered = isCovered;
        this.quantity = quantity;
        this.claimedAmount = claimedAmount;
        this.sourceDocument = sourceDocument;
    }
}
