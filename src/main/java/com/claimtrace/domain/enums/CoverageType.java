package com.claimtrace.domain.enums;

/**
 * 담보의 분류.
 *
 * <p>원인(질병·상해)과 급여 여부(급여·비급여)의 조합이다. 실손의료보험의
 * 담보 구조를 따른다.
 *
 * <p>이 값이 두 곳에서 판단에 쓰인다.
 * <ul>
 *   <li>{@code rules.coverage_type} — 룰이 어느 담보에 적용되는지</li>
 *   <li>{@code intervention_rules.conditions} — 개입 규칙 발동 조건의 필드</li>
 * </ul>
 *
 * <p>비급여 항목이 개입 규칙의 주요 대상이 되는 이유는, 급여 항목은
 * 건강보험 심사평가원 기준이 이미 적용된 뒤라 판단의 여지가 좁고
 * 비급여는 의료기관이 가격과 시행 빈도를 정하는 영역이라 분쟁이 잦기
 * 때문이다.
 */
public enum CoverageType {

    /** 질병 급여. 질병으로 인한 진료 중 건강보험 급여 대상. */
    DISEASE_COVERED("질병 급여"),

    /** 질병 비급여. 질병으로 인한 진료 중 건강보험 비급여 대상. */
    DISEASE_UNCOVERED("질병 비급여"),

    /** 상해 급여. 상해로 인한 진료 중 건강보험 급여 대상. */
    INJURY_COVERED("상해 급여"),

    /** 상해 비급여. 상해로 인한 진료 중 건강보험 비급여 대상. */
    INJURY_UNCOVERED("상해 비급여");

    private final String label;

    CoverageType(String label) {
        this.label = label;
    }

    /**
     * 화면에 표시할 한글 명칭을 반환한다.
     *
     * @return 한글 라벨
     */
    public String getLabel() {
        return label;
    }
}
