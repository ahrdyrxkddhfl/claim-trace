package com.claimtrace.domain;

import java.math.BigDecimal;
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
 * AI 의 항목별 권고.
 *
 * <p>{@link Review} 와 물리적으로 분리된 테이블이라는 점이 G1 이고 D-1 이다.
 * 하나의 항목에 컬럼 두 개({@code ai_decision}, {@code final_decision})를 두는
 * 대안을 기각한 이유는 이렇다. 모델 버전이 바뀌면 한 항목에 권고가 여러 건
 * 쌓이는데, 두 컬럼 방식에서는 개입 이력이 어느 권고에 대한 것인지 지목할 수
 * 없고 "AI 권고가 몇 건 뒤집혔는가"를 집계할 수 없다. 보조수단성 ⑧이 요구하는
 * "인적 개입 이행 결과의 기록"이 성립하지 않는다.
 *
 * <p>{@code isLatest} 가 참인 레코드가 현재 유효한 권고다. 판정 저장 서비스가
 * 이 레코드의 {@code recommendation} 과 심사자의 판정을 비교해, 다르면
 * 개입 기록을 생성한다(D-7, INV-3).
 *
 * <p>{@code threshold} 를 함께 저장하는 이유는 임계값이 운영 중에 조정되기
 * 때문이다. 확률 0.82 가 부지급 권고였는지 지급 권고였는지는 그 시점의
 * 임계값을 알아야 재현된다. 산출 결과만 남기고 기준을 남기지 않으면
 * 사후에 권고를 검증할 수 없다.
 */
@Entity
@Table(
        name = "ai_recommendations",
        indexes = {
                @Index(name = "idx_ai_rec_item_latest", columnList = "claim_item_id, is_latest"),
                @Index(name = "idx_ai_rec_item_created", columnList = "claim_item_id, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 권고 대상 항목. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_item_id", nullable = false)
    private ClaimItem claimItem;

    /** 모델 이름. 예: claim-risk */
    @Column(nullable = false, length = 50)
    private String modelName;

    /** 모델 버전. 예: v2.3 */
    @Column(nullable = false, length = 20)
    private String modelVersion;

    /** 보상제외 확률. 0.000~1.000 범위다. */
    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal exclusionProbability;

    /** 확률과 임계값으로 산출된 권고. 심사자의 판정과 비교되는 값이다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemDecision recommendation;

    /** 산출 시점의 자동처리 임계값. 사후 재현을 위해 함께 보존한다. */
    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal threshold;

    /** 현재 유효한 권고인지 여부. 새 권고가 생기면 이전 것이 거짓으로 바뀐다. */
    @Column(nullable = false)
    private boolean isLatest;

    /** 산출 시각. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * AI 권고를 생성한다.
     *
     * <p>새로 만들어진 권고는 언제나 최신이므로 {@code isLatest} 를 생성자에서
     * 받지 않는다. 이전 권고를 물리는 것은 {@link #supersede()} 의 책임이다.
     *
     * @param claimItem 권고 대상 항목
     * @param modelName 모델 이름
     * @param modelVersion 모델 버전
     * @param exclusionProbability 보상제외 확률
     * @param recommendation 산출된 권고
     * @param threshold 산출 시점의 임계값
     */
    @Builder
    private AiRecommendation(ClaimItem claimItem, String modelName, String modelVersion,
                             BigDecimal exclusionProbability, ItemDecision recommendation,
                             BigDecimal threshold) {
        this.claimItem = claimItem;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.exclusionProbability = exclusionProbability;
        this.recommendation = recommendation;
        this.threshold = threshold;
        this.isLatest = true;
    }

    /**
     * 이 권고를 더 이상 최신이 아닌 것으로 표시한다.
     *
     * <p>새 모델 버전의 권고가 도착했을 때 호출한다. 레코드를 지우거나
     * 갱신하지 않고 플래그만 내리므로, 어느 시점에 어떤 권고가 있었는지가
     * 그대로 남는다.
     */
    public void supersede() {
        this.isLatest = false;
    }

    /**
     * 주어진 판정이 이 권고를 뒤집는 것인지 판별한다.
     *
     * <p>판정 저장 서비스가 개입 기록 생성 여부를 결정할 때 호출한다(D-7).
     * 심사자가 개입 여부를 스스로 선언하지 않고 시스템이 이 비교로 판별하는
     * 것이 INV-3 의 강제 방식이다.
     *
     * @param decision 심사자가 내린 판정
     * @return 판정이 권고와 다르면 {@code true}
     */
    public boolean isOverriddenBy(ItemDecision decision) {
        return this.recommendation != decision;
    }
}
