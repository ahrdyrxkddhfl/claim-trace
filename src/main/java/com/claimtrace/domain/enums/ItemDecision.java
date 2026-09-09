package com.claimtrace.domain.enums;

/**
 * 청구 항목에 대한 지급 판정.
 *
 * <p>같은 Enum 이 두 자리에서 쓰이며, 두 자리를 물리적으로 분리한 것이 G1 이다.
 * <ul>
 *   <li>{@code ai_recommendations.recommendation} — AI 가 산출한 권고</li>
 *   <li>{@code reviews.decision} — 사람이 내린 판정</li>
 * </ul>
 *
 * <p>판정 저장 시 이 두 값을 비교해 다르면 개입 기록을 생성한다(D-7, INV-3).
 * 심사자가 개입 여부를 스스로 선언하지 않고 시스템이 비교해 판별하는 것이
 * 이 설계의 핵심이다.
 *
 * <p>{@code PARTIAL} 은 청구금액의 일부만 지급하는 판정이며, 지급액은
 * {@code reviews.paid_amount} 에 별도로 기록된다. 즉 판정 값만으로는
 * 금액을 알 수 없다.
 */
public enum ItemDecision {

    /** 지급. 청구금액 전액을 지급한다. */
    PAY("지급"),

    /** 일부지급. 청구금액 중 일부만 지급한다. 지급액은 별도 컬럼에 기록된다. */
    PARTIAL("일부지급"),

    /** 부지급. 지급하지 않는다. 고객 설명문에 부정적 근거가 필수다(INV-5). */
    DENY("부지급");

    private final String label;

    ItemDecision(String label) {
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
     * 고객 설명문 작성 시 부정적 근거가 필요한 판정인지 판별한다.
     *
     * <p>INV-5 는 부지급·일부지급 항목에 {@code polarity = NEGATIVE} 인
     * 고객용 채택 근거가 1건 이상 포함될 것을 요구한다. 설명문 초안 생성
     * 서비스가 검사 대상 항목을 고를 때 이 메서드를 쓴다.
     *
     * @return 부지급 또는 일부지급이면 {@code true}
     */
    public boolean requiresNegativeEvidence() {
        return this == DENY || this == PARTIAL;
    }
}
