package com.claimtrace.domain.enums;

/**
 * 설명서의 범위.
 *
 * <p>가이드라인 신뢰성 원칙의 설명가능성 요구를 두 층으로 나눈 것이다.
 * 전역 설명은 모델이 일반적으로 어떤 요소를 보는지에 대한 것이고,
 * 국소 설명은 특정 청구가 왜 그렇게 판정되었는지에 대한 것이다.
 *
 * <p>두 유형은 채워지는 컬럼이 다르다.
 * <ul>
 *   <li>{@code GLOBAL} — {@code model_name} 이 채워지고 {@code claim_id} 는 null</li>
 *   <li>{@code LOCAL} — {@code claim_id} 가 채워지고 {@code due_date} 가 필수(INV-8)</li>
 * </ul>
 *
 * <p>국소 설명에만 기한이 있는 이유는, 전역 설명은 모델별로 미리 만들어
 * 두는 것이고 국소 설명은 청구인의 신청에 응답하는 것이기 때문이다.
 */
public enum ExplanationType {

    /** 전역 설명. 모델 단위로 사전 생성되며 특정 청구에 매이지 않는다. */
    GLOBAL("전역 설명"),

    /** 국소 설명. 청구별로 신청을 받아 생성하며 제공 기한이 있다(INV-8). */
    LOCAL("국소 설명");

    private final String label;

    ExplanationType(String label) {
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

    /**
     * 제공 기한이 필수인 유형인지 판별한다.
     *
     * <p>INV-8 은 국소 설명서에 {@code due_date} 가 존재할 것을 요구한다.
     * 조건부 NOT NULL 이라 DB 제약만으로는 표현되지 않으므로 저장 시점에
     * 이 메서드로 검사한다.
     *
     * @return 국소 설명이면 {@code true}
     */
    public boolean requiresDueDate() {
        return this == LOCAL;
    }
}
