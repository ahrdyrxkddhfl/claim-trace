package com.claimtrace.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.claimtrace.domain.enums.InterventionType;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인적 개입 규칙. 발동 조건을 코드가 아니라 데이터로 둔다.
 *
 * <p>D-5 의 실체다. 복수인 확인 조건을 코드에 하드코딩하는 대안을 기각한
 * 이유는 보조수단성 점검항목 ⑤가 "인적 개입 방식을 적용하는 기준이
 * <b>문서화</b>되어 있는가"를 묻기 때문이다. 코드에 묻힌 조건은 심사관리자가
 * 확인할 수도 변경할 수도 없다.
 *
 * <p>{@code conditions} 는 조건 객체의 배열을 담은 JSON 문자열이다. 각
 * 조건은 {@code field}, {@code op}, {@code value} 세 키를 가지며 모든 조건이
 * AND 로 결합된다. 예를 들면 이런 모양이다.
 *
 * <pre>
 * [
 *   {"field": "coverageType", "op": "eq",  "value": "DISEASE_UNCOVERED"},
 *   {"field": "claimedAmount", "op": "gte", "value": 300000}
 * ]
 * </pre>
 *
 * <p>본 구현이 지원하는 {@code field} 는 {@code claimedAmount},
 * {@code exclusionProbability}, {@code coverageType} 세 가지이고 {@code op} 는
 * {@code gte}, {@code lte}, {@code eq} 세 가지다. 기술서와 화면에 등장하는
 * 규칙은 이 조합으로 모두 표현된다. 범용 조건 평가기을 만드는 것은 이 프로젝트가
 * 증명하려는 명제와 무관하므로 의도적으로 넓히지 않았다.
 *
 * <p>JSON 을 DB 의 JSON 타입이 아니라 문자열로 저장한다. H2 와 PostgreSQL 의
 * JSON 타입 취급이 다르고, 본 구현은 JSON 내부를 SQL 로 질의하지 않고
 * 애플리케이션에서 파싱해 평가한다.
 *
 * <p>규칙을 수정해도 이미 저장된 판정에 소급 적용되지 않는다. 발동된 개입은
 * 어느 규칙에 의한 것인지를 {@link Intervention} 이 FK 로 들고 있으므로,
 * 조건이 바뀌어도 과거 개입의 근거는 그대로 남는다.
 */
@Entity
@Table(name = "intervention_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterventionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 규칙 코드. P-NN 형식이며 규칙을 식별하는 자연키다. */
    @Column(nullable = false, unique = true, length = 10)
    private String code;

    /** 규칙명. 예: 비급여 고액 항목 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 발동 조건 배열의 JSON 문자열. 모든 조건이 AND 로 결합된다. */
    @Column(nullable = false, columnDefinition = "text")
    private String conditions;

    /** 요구되는 개입 유형. DUAL_CHECK 또는 ESCALATION 이다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterventionType requiredIntervention;

    /** 승인 가능 역할 배열의 JSON 문자열. 예: ["REVIEW_MANAGER"] */
    @Column(nullable = false, columnDefinition = "text")
    private String approverRoles;

    /** 활성 여부. 비활성 규칙은 새 개입을 요구하지 않는다. */
    @Column(nullable = false)
    private boolean active;

    /** 이 규칙을 작성한 심사관리자. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    /** 규칙 생성 시각. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 최종 수정 시각. 수정된 적이 없으면 {@code null}. */
    private LocalDateTime updatedAt;

    /**
     * 개입 규칙을 생성한다.
     *
     * @param code 규칙 코드
     * @param name 규칙명
     * @param conditions 발동 조건 배열의 JSON 문자열
     * @param requiredIntervention 요구되는 개입 유형
     * @param approverRoles 승인 가능 역할 배열의 JSON 문자열
     * @param active 활성 여부
     * @param author 작성한 심사관리자
     */
    @Builder
    private InterventionRule(String code, String name, String conditions,
                               InterventionType requiredIntervention, String approverRoles,
                               boolean active, User author) {
        this.code = code;
        this.name = name;
        this.conditions = conditions;
        this.requiredIntervention = requiredIntervention;
        this.approverRoles = approverRoles;
        this.active = active;
        this.author = author;
    }
}
