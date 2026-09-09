package com.claimtrace.domain.enums;

/**
 * 이의제기의 처리 상태.
 *
 * <p>{@code RECEIVED → REVIEWING → ANSWERED} 로 진행한다.
 * 재검토 결과는 기존 판정을 갱신하지 않고 {@code reviews} 에 새 레코드로
 * 저장되며 최초 판정은 이력으로 남는다(D-6). 판정이 뒤집히는 사건이
 * 가장 설명을 요구하는 사건인데 덮어쓰면 "최초에 왜 부지급이었는가"가
 * 소실되기 때문이다.
 *
 * <p>재검토 심사자는 최초 판정자와 달라야 한다. 이 제약은 DB 가 아니라
 * 애플리케이션에서 검사한다.
 */
public enum ObjectionStatus {

    /** 접수. 청구인이 이의를 제기했고 아직 배정되지 않은 상태. */
    RECEIVED("접수"),

    /** 재검토중. 최초 판정자가 아닌 심사자가 다시 보는 상태. */
    REVIEWING("재검토중"),

    /** 회신 완료. 재검토 결과를 청구인에게 전달한 상태. */
    ANSWERED("회신 완료");

    private final String label;

    ObjectionStatus(String label) {
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
