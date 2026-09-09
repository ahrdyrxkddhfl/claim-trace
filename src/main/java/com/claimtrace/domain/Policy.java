package com.claimtrace.domain;

import java.time.LocalDate;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보험 계약.
 *
 * <p>청구인이 가입한 증권 한 건이며, 담보({@link Coverage})를 여러 개 갖는다.
 * 청구({@link Claim})는 계약과 청구인 양쪽을 참조한다. 계약만 참조해도
 * 청구인을 따라갈 수 있지만, 화면 8 심사 큐가 청구인 기준으로 조회하고
 * INV-6 소유권 검사도 청구인을 직접 비교하므로 조인을 한 단계 줄인다.
 *
 * <p>{@code endedOn} 이 {@code null} 이면 유지 중인 계약이다. 종료일을 두고
 * 삭제하지 않는 것은, 만료된 계약에 대해서도 만료 전 진료에 대한 청구가
 * 들어올 수 있고 그 심사에 계약 조건이 필요하기 때문이다.
 *
 * <p>연관관계는 모두 지연 로딩이다. 즉시 로딩이면 청구 목록 하나를 읽을 때
 * 계약과 청구인까지 매번 따라와 화면 8 같은 목록 조회에서 쿼리가 폭증한다.
 */
@Entity
@Table(name = "policies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 계약자. 이 계약을 보유한 청구인이다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** 증권번호. 계약을 식별하는 자연키다. */
    @Column(nullable = false, unique = true, length = 30)
    private String policyNo;

    /** 상품명. 예: 종합실손의료비보험 4세대 */
    @Column(nullable = false, length = 100)
    private String productName;

    /** 보장 개시일. */
    @Column(nullable = false)
    private LocalDate startedOn;

    /** 보장 종료일. {@code null} 이면 유지 중인 계약이다. */
    private LocalDate endedOn;

    /** 계약 등록 시각. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 보험 계약을 생성한다.
     *
     * @param customer 계약자
     * @param policyNo 증권번호
     * @param productName 상품명
     * @param startedOn 보장 개시일
     * @param endedOn 보장 종료일. 유지 중이면 {@code null}
     */
    @Builder
    private Policy(Customer customer, String policyNo, String productName,
                   LocalDate startedOn, LocalDate endedOn) {
        this.customer = customer;
        this.policyNo = policyNo;
        this.productName = productName;
        this.startedOn = startedOn;
        this.endedOn = endedOn;
    }
}
