package com.claimtrace.domain.enums;

/**
 * 청구 건의 진행 상태.
 *
 * <p>정상 흐름은 {@code RECEIVED → UNDER_REVIEW → DECIDED → CLOSED} 이며,
 * 청구인이 이의를 제기하면 {@code DECIDED → OBJECTION → DECIDED} 로 되돌아온다.
 * 이때 기존 판정을 덮어쓰지 않고 {@code reviews} 에 새 레코드를 쌓는다(D-6).
 *
 * <p>이 값은 두 곳에서 판단 근거가 된다.
 * <ul>
 *   <li>INV-7 — {@code DECIDED} 인 청구의 근거는 변경할 수 없다. 위반 시 409(E-6).</li>
 *   <li>판정 확정 — 이미 {@code DECIDED} 인 청구를 다시 확정하려 하면 409.</li>
 * </ul>
 *
 * <p><b>정의되지 않은 지점</b> — 설계 문서는 {@code OBJECTION} 상태에서 근거 수정을
 * 허용하는지 규정하지 않았다. 현재 구현은 API 명세 문구를 따라 {@code DECIDED} 만
 * 차단한다. 이 공백은 기술서 7.5에 남긴다.
 */
public enum ClaimStatus {

    /** 접수. 청구인이 서류를 제출했고 아직 배당되지 않은 상태. */
    RECEIVED("접수"),

    /** 심사중. 심사자에게 배당되어 항목별 판정이 진행 중인 상태. */
    UNDER_REVIEW("심사중"),

    /** 판정 확정. 모든 항목이 판정되고 필요한 개입 승인이 끝난 상태. */
    DECIDED("판정 확정"),

    /** 이의제기 접수. 확정된 판정에 청구인이 이의를 제기한 상태. */
    OBJECTION("이의제기 접수"),

    /** 종결. 지급 또는 회신이 완료되어 더 이상 변경되지 않는 상태. */
    CLOSED("종결");

    private final String label;

    ClaimStatus(String label) {
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
