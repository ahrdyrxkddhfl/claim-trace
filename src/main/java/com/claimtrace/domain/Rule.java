package com.claimtrace.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.claimtrace.domain.enums.CoverageType;
import com.claimtrace.domain.enums.DisclosureLevel;
import com.claimtrace.domain.enums.Polarity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 심사 룰과 약관 조항. 버전 단위로 보존된다.
 *
 * <p>PK 는 {@code id} 지만 자연키는 {@code (code, version)} 이다. 이 구분이
 * 설계의 핵심 중 하나다. 근거({@link Evidence})의 {@code ruleId} 는 특정
 * 버전을 가리키므로, 룰을 수정해 새 버전이 생겨도 과거에 만들어진 근거는
 * 그대로 남는다. 3년 전 판정의 근거가 오늘의 약관 해석으로 바뀌지 않는다.
 *
 * <p>수정은 갱신이 아니라 새 레코드 생성이다. 버전 번호가 1 증가하고 이전
 * 버전은 삭제되지 않는다(설계 원칙 3). 화면 14 의 버전 이력이 이 구조 위에
 * 만들어진다.
 *
 * <p>문구를 {@code contentInternal} 과 {@code contentCustomer} 로 나눈 것은
 * 같은 조항이라도 심사자에게 보일 표현과 고객에게 보일 표현이 다르기
 * 때문이다. 고객용 문구는 약관 용어를 일반 표현으로 옮겨 작성한다.
 * 이 두 값이 근거 생성 시 그대로 복사되어 근거의 문구가 된다.
 *
 * <p>{@code active} 가 거짓인 룰은 새 근거를 만들지 않지만 기존 근거의
 * 참조는 유지된다. 비활성화가 삭제가 아닌 이유도 같다.
 */
@Entity
@Table(
        name = "rules",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rules_code_version",
                columnNames = {"code", "version"}
        ),
        indexes = @Index(name = "idx_rules_coverage_active", columnList = "coverage_type, active")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Rule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 룰 코드. R-NNNN 형식이며 버전이 달라도 같은 값을 유지한다. */
    @Column(nullable = false, length = 20)
    private String code;

    /** 버전 번호. 수정 시 1 증가한 새 레코드가 생성된다. */
    @Column(nullable = false)
    private Short version;

    /** 룰 이름. 화면 14 목록에 표시된다. */
    @Column(nullable = false, length = 100)
    private String name;

    /** 적용 담보 분류. 어느 담보의 항목에 이 룰을 매칭할지 가른다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CoverageType coverageType;

    /** 근거 약관 조항. 예: 제12조 3항 */
    @Column(nullable = false, length = 50)
    private String articleNo;

    /** 조항 원문. 화면 10 우측 상세에 그대로 노출된다. */
    @Column(nullable = false, columnDefinition = "text")
    private String articleText;

    /** 이 룰이 가리키는 방향. 여기서 만들어진 근거가 극성을 물려받는다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Polarity polarity;

    /** 적용 조건식. 본 구현에서는 평가하지 않고 화면 표시용으로만 보관한다. */
    @Column(nullable = false, columnDefinition = "text")
    private String conditionExpr;

    /** 심사자용 근거 문구. 근거 생성 시 복사된다. */
    @Column(nullable = false, columnDefinition = "text")
    private String contentInternal;

    /** 고객 설명문용 문구. 약관 용어를 일반 표현으로 옮겨 작성한다. */
    @Column(nullable = false, columnDefinition = "text")
    private String contentCustomer;

    /** 이 룰에서 생성되는 근거의 기본 공개 수준. 룰 근거는 통상 CUSTOMER 다(D-4). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DisclosureLevel defaultDisclosureLevel;

    /** 활성 여부. 비활성 룰은 새 근거를 만들지 않으나 기존 참조는 유지된다. */
    @Column(nullable = false)
    private boolean active;

    /** 이 버전을 작성한 사용자. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    /** 이 버전의 변경 내용. 화면 14 버전 이력에 표시된다. */
    @Column(length = 255)
    private String changeNote;

    /** 이 버전의 생성 시각. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 룰의 한 버전을 생성한다.
     *
     * @param code 룰 코드
     * @param version 버전 번호
     * @param name 룰 이름
     * @param coverageType 적용 담보 분류
     * @param articleNo 근거 약관 조항
     * @param articleText 조항 원문
     * @param polarity 이 룰이 가리키는 방향
     * @param conditionExpr 적용 조건식
     * @param contentInternal 심사자용 근거 문구
     * @param contentCustomer 고객 설명문용 문구
     * @param defaultDisclosureLevel 기본 공개 수준. {@code null} 이면 CUSTOMER 로 채운다
     * @param active 활성 여부
     * @param author 작성자
     * @param changeNote 변경 내용. 최초 버전이면 {@code null}
     */
    @Builder
    private Rule(String code, Short version, String name, CoverageType coverageType,
                 String articleNo, String articleText, Polarity polarity, String conditionExpr,
                 String contentInternal, String contentCustomer, DisclosureLevel defaultDisclosureLevel,
                 boolean active, User author, String changeNote) {
        this.code = code;
        this.version = version;
        this.name = name;
        this.coverageType = coverageType;
        this.articleNo = articleNo;
        this.articleText = articleText;
        this.polarity = polarity;
        this.conditionExpr = conditionExpr;
        this.contentInternal = contentInternal;
        this.contentCustomer = contentCustomer;
        this.defaultDisclosureLevel =
                defaultDisclosureLevel == null ? DisclosureLevel.CUSTOMER : defaultDisclosureLevel;
        this.active = active;
        this.author = author;
        this.changeNote = changeNote;
    }
}
