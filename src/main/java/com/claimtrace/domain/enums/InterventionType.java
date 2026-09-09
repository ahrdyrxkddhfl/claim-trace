package com.claimtrace.domain.enums;

/**
 * 인적 개입의 유형.
 *
 * <p>세 유형이 생성되는 경로가 서로 다르다.
 * <ul>
 *   <li>{@code OVERRIDE} — 판정 저장 시 시스템이 자동 생성한다. 심사자가
 *       선언하지 않는다(D-7). 최신 AI 권고와 판정이 다르면 같은 트랜잭션에서
 *       생성된다.</li>
 *   <li>{@code DUAL_CHECK} — 개입 정책 조건에 걸린 청구에 대해 요구된다.
 *       승인 전에는 확정할 수 없다(INV-4).</li>
 *   <li>{@code ESCALATION} — 정책이 차상위 검토를 요구하는 경우.</li>
 * </ul>
 *
 * <p>{@code DUAL_CHECK} 와 {@code ESCALATION} 은 {@code approved} 필드를
 * 사용하고 {@code OVERRIDE} 는 사용하지 않는다. 오버라이드는 승인 대상이
 * 아니라 이미 일어난 사실의 기록이기 때문이다.
 */
public enum InterventionType {

    /** AI 권고 미채택. 판정이 권고와 달라 시스템이 생성한 기록. */
    OVERRIDE("AI 권고 미채택"),

    /** 복수인 확인. 판정자 외 다른 사람의 승인이 필요하다. */
    DUAL_CHECK("복수인 확인"),

    /** 차상위 검토. 상위 권한자의 검토가 필요하다. */
    ESCALATION("차상위 검토");

    private final String label;

    InterventionType(String label) {
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
     * 승인 절차를 거쳐야 하는 개입 유형인지 판별한다.
     *
     * <p>판정 확정 서비스가 INV-4 를 검사할 때, 승인 대기 중인 개입이
     * 남아 있는지 고르는 기준으로 쓴다. {@code OVERRIDE} 는 승인 대상이
     * 아니므로 이 검사에서 제외된다.
     *
     * @return 복수인 확인 또는 차상위 검토이면 {@code true}
     */
    public boolean requiresApproval() {
        return this == DUAL_CHECK || this == ESCALATION;
    }
}
