package com.claimtrace.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.claimtrace.domain.enums.ItemDecision;

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
 * 사람의 항목별 판정. 이력으로 쌓인다.
 *
 * <p>{@link AiRecommendation} 과 분리된 테이블이라는 점이 G1 의 절반이다.
 * "누가 결정했는가"를 보존하기 위해 AI 의 권고와 사람의 판정을 물리적으로
 * 다른 곳에 둔다.
 *
 * <p>재검토 시 기존 레코드를 갱신하지 않고 새 레코드를 추가하며
 * {@code isCurrent} 를 이관한다(D-6). 이관은 {@link #supersede()} 와
 * {@link #linkSuccessor(Review)} 두 단계로 나뉘는데, 그 이유는 각 메서드에
 * 적었다. 이의제기로 판정이 뒤집히는 사건은
 * 가장 설명이 필요한 사건인데, 덮어쓰면 "최초에 왜 부지급이었는가"가
 * 소실된다. 이의제기 회신에도 그 정보가 필요하다.
 *
 * <p><b>INV-12 와 H2 의 제약</b> — 항목당 {@code isCurrent = true} 인 레코드는
 * 정확히 1건이어야 한다. PostgreSQL 이라면
 * {@code CREATE UNIQUE INDEX ... WHERE is_current} 로 DB 가 강제할 수 있지만
 * H2 는 부분 유니크 인덱스를 지원하지 않는다. 본 구현에서는 판정 저장
 * 서비스가 항목 행에 배타 잠금을 걸어 동시 요청을 직렬화하는 것으로
 * 보장하고, 그 보장이 실제로 성립하는지를 테스트로 확인한다.
 *
 * <p>PostgreSQL 프로파일에서는 사정이 다르다.
 * {@code CREATE UNIQUE INDEX ... WHERE is_current} 로 DB 가 직접 강제하므로,
 * 애플리케이션의 잠금을 우회하더라도 두 번째 현재 판정은 저장되지 않는다.
 * 같은 불변조건을 두 층에서 지키는 셈이고, 어느 층이 무엇을 보장하는지는
 * README 에 구분해 적는다.
 *
 * <p>{@code paidAmount} 를 판정과 별도 컬럼으로 두는 이유는 {@code PARTIAL}
 * 때문이다. 일부지급이라는 판정만으로는 얼마를 지급하는지 알 수 없다.
 */
@Entity
@Table(
        name = "reviews",
        indexes = {
                @Index(name = "idx_reviews_item_current", columnList = "claim_item_id, is_current"),
                @Index(name = "idx_reviews_reviewer_decided", columnList = "reviewer_id, decided_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 판정 대상 항목. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_item_id", nullable = false)
    private ClaimItem claimItem;

    /** 판정을 내린 심사자. INV-1 이 요구하는 행위자다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    /** 지급 판정. AI 권고와 비교되어 개입 기록 생성 여부를 결정한다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemDecision decision;

    /** 지급 결정액(원). 부지급이면 0 이다. */
    @Column(nullable = false)
    private Integer paidAmount;

    /** 판정 사유. INV-2 가 요구하며 공백일 수 없다. */
    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    /** 현재 유효한 판정인지 여부. 항목당 정확히 1건만 참이어야 한다(INV-12). */
    @Column(nullable = false)
    private boolean isCurrent;

    /** 이 판정을 대체한 판정. 아직 유효한 판정이면 {@code null}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "superseded_by")
    private Review supersededBy;

    /** 판정 시각. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime decidedAt;

    /**
     * 판정을 생성한다.
     *
     * <p>새 판정은 언제나 현재 판정이므로 {@code isCurrent} 를 받지 않는다.
     * 이전 판정을 물리는 것은 {@link #supersededBy(Review)} 의 책임이며,
     * 두 작업이 같은 트랜잭션에서 일어나야 INV-12 가 성립한다.
     *
     * <p>사유가 비어 있으면 예외를 던진다. 컬럼이 NOT NULL 이라도 공백
     * 문자열은 통과하므로 DB 제약만으로는 INV-2 를 지킬 수 없다. 이 검사가
     * 마지막 방어선이고, 요청 검증 계층에서도 같은 조건을 확인한다.
     *
     * @param claimItem 판정 대상 항목
     * @param reviewer 판정을 내린 심사자
     * @param decision 지급 판정
     * @param paidAmount 지급 결정액(원)
     * @param reason 판정 사유
     * @throws IllegalArgumentException 사유가 {@code null} 이거나 공백뿐인 경우
     */
    @Builder
    private Review(ClaimItem claimItem, User reviewer, ItemDecision decision,
                   Integer paidAmount, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("판정 사유를 입력해야 저장할 수 있습니다 (INV-2)");
        }
        this.claimItem = claimItem;
        this.reviewer = reviewer;
        this.decision = decision;
        this.paidAmount = paidAmount == null ? 0 : paidAmount;
        this.reason = reason;
        this.isCurrent = true;
    }

    /**
     * 현재 판정 플래그를 내린다.
     *
     * <p><b>대체 판정 연결과 분리한 이유는 DB 제약 때문이다.</b> PostgreSQL
     * 프로파일에서는 {@code (claim_item_id) WHERE is_current} 에 부분 UNIQUE
     * 인덱스가 걸린다. 새 판정을 먼저 저장하면 그 INSERT 시점에 현재 판정이
     * 잠깐 2건이 되어 인덱스가 이를 거부한다. 따라서 이전 판정의 플래그를
     * 먼저 내려 반영한 뒤에야 새 판정을 저장할 수 있다.
     *
     * <p>그런데 {@code superseded_by} 에 넣을 새 판정의 식별자는 저장 후에야
     * 생긴다. 한 메서드로 두 변경을 함께 할 수 없는 것이 여기서 온다.
     * 두 호출 사이의 중간 상태는 같은 트랜잭션 안에만 존재하며, 판정 저장
     * 서비스가 둘을 반드시 함께 수행한다.
     *
     * @see #linkSuccessor(Review)
     */
    public void supersede() {
        this.isCurrent = false;
    }

    /**
     * 이 판정을 대체한 판정을 연결한다.
     *
     * <p>{@link #supersede()} 로 플래그를 내린 뒤, 새 판정이 저장되어 식별자를
     * 가진 시점에 호출한다.
     *
     * @param newer 이 판정을 대체하는 새 판정
     */
    public void linkSuccessor(Review newer) {
        this.supersededBy = newer;
    }
}
