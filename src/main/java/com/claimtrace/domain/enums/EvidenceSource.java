package com.claimtrace.domain.enums;

/**
 * 근거가 생성된 출처.
 *
 * <p>기술서 1.2 가 관찰한 "모델 권고와 룰 판정 두 층"에 심사자 직접 입력을
 * 더한 세 가지다. 출처에 따라 {@code evidences} 의 어느 FK 가 채워지는지,
 * 그리고 공개 수준 기본값이 무엇인지가 갈린다.
 *
 * <table border="1">
 *   <caption>출처별 FK 와 기본 공개 수준</caption>
 *   <tr><th>출처</th><th>채워지는 FK</th><th>기본 공개 수준</th></tr>
 *   <tr><td>{@code AI}</td><td>{@code ai_recommendation_id}</td><td>INTERNAL</td></tr>
 *   <tr><td>{@code RULE}</td><td>{@code rule_id}</td><td>CUSTOMER</td></tr>
 *   <tr><td>{@code MANUAL}</td><td>없음</td><td>심사자가 선택</td></tr>
 * </table>
 *
 * <p>기본값 분기의 근거는 D-4 다. 새플리 기여도는 그대로 고객에게 보이면
 * 설명이 아니라 숫자 나열이 되므로 내부용으로 시작한다.
 */
public enum EvidenceSource {

    /** 모델 기여도 기반. 새플리 값에서 파생된 근거이며 기본 공개 수준은 INTERNAL. */
    AI("모델 기여도 기반"),

    /** 심사 룰 매칭. 약관 조항에 연결되며 기본 공개 수준은 CUSTOMER. */
    RULE("심사 룰 매칭"),

    /** 심사자 직접 추가. 화면 10 에서 생성되며 상태가 즉시 ADOPTED 다. */
    MANUAL("심사자 직접 추가");

    private final String label;

    EvidenceSource(String label) {
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
