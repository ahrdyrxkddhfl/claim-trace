package com.claimtrace.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.claimtrace.domain.enums.CoverageType;

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
 * 담보·특약.
 *
 * <p>계약이 실제로 무엇을 얼마까지 보장하는지를 담는다. 청구 항목
 * ({@link ClaimItem})은 반드시 하나의 담보에 귀속되며, 그 담보의 한도와
 * 자기부담률이 지급액 산정의 기준이 된다.
 *
 * <p>이 엔티티의 값들이 근거의 원천이 된다는 점이 중요하다. "한도 초과로
 * 일부지급"이라는 판단은 {@code limitAmount} 에서, "면책기간 중 발생"이라는
 * 판단은 {@code exemptionUntil} 에서 나온다. 이 값들이 룰의 조건식
 * ({@code rules.condition_expr})이 참조하는 대상이다.
 *
 * <p>{@code deductibleRate} 를 {@link BigDecimal} 로 둔 이유는 금액 계산에
 * 쓰이기 때문이다. {@code double} 은 0.3 을 정확히 표현하지 못해 지급액에
 * 원 단위 오차가 생기고, 그 오차는 심사 결과에 대한 신뢰를 직접 깎는다.
 */
@Entity
@Table(
        name = "coverages",
        indexes = @Index(name = "idx_coverages_policy_type", columnList = "policy_id, type")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coverage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 담보가 속한 계약. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    /** 담보·특약명. 예: 질병 비급여 통원 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 담보 분류. 개입 정책의 발동 조건 필드이기도 하다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CoverageType type;

    /** 1회당 지급 한도(원). 한도 초과 판단의 기준값이다. */
    @Column(nullable = false)
    private Integer limitAmount;

    /** 연간 횟수 한도. {@code null} 이면 횟수 제한이 없다. */
    private Integer annualLimit;

    /** 자기부담률. 0.000~1.000 범위이며 지급액 산정에 쓰인다. */
    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal deductibleRate;

    /** 면책기간 종료일. 이 날짜 이전 진료는 보장 대상이 아니다. {@code null} 이면 면책기간이 없다. */
    private LocalDate exemptionUntil;

    /**
     * 담보를 생성한다.
     *
     * @param policy 이 담보가 속한 계약
     * @param name 담보·특약명
     * @param type 담보 분류
     * @param limitAmount 1회당 지급 한도(원)
     * @param annualLimit 연간 횟수 한도. 제한이 없으면 {@code null}
     * @param deductibleRate 자기부담률. {@code null} 이면 0 으로 채운다
     * @param exemptionUntil 면책기간 종료일. 없으면 {@code null}
     */
    @Builder
    private Coverage(Policy policy, String name, CoverageType type, Integer limitAmount,
                     Integer annualLimit, BigDecimal deductibleRate, LocalDate exemptionUntil) {
        this.policy = policy;
        this.name = name;
        this.type = type;
        this.limitAmount = limitAmount;
        this.annualLimit = annualLimit;
        this.deductibleRate = deductibleRate == null ? BigDecimal.ZERO : deductibleRate;
        this.exemptionUntil = exemptionUntil;
    }
}
