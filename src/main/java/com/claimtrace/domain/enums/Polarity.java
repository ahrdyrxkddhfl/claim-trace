package com.claimtrace.domain.enums;

/**
 * 근거가 가리키는 방향.
 *
 * <p>하나의 항목에 지급 방향 근거와 보상제외 방향 근거가 함께 존재할 수 있고,
 * 심사자는 양쪽을 보고 판정한다. 이 값은 {@code rules} 와 {@code evidences}
 * 양쪽에 있으며, 룰에서 생성된 근거는 룰의 극성을 물려받는다.
 *
 * <p>INV-5 가 이 값을 직접 센다. 부지급·일부지급 항목의 고객 설명문에는
 * {@code NEGATIVE} 인 고객용 채택 근거가 1건 이상 있어야 한다. 지급하지
 * 않겠다고 하면서 그 이유를 대지 못하는 설명문을 막는 조건이다.
 */
public enum Polarity {

    /** 지급 방향. 이 근거는 지급을 뒷받침한다. */
    POSITIVE("지급 방향"),

    /** 보상제외 방향. 이 근거는 부지급·감액을 뒷받침한다. */
    NEGATIVE("보상제외 방향");

    private final String label;

    Polarity(String label) {
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
