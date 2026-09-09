package com.claimtrace.domain.enums;

/**
 * 근거의 검토 상태.
 *
 * <p>생성된 근거는 심사자가 화면 10 에서 채택하거나 기각한다.
 * 기각된 근거를 삭제하지 않는 것이 D-3 이며, 근거는 RACI 참고사항의
 * "채택·미채택 사유 기재는 의무사항"에 대응한다. 이의제기 재검토 시
 * "무엇을 검토했고 왜 배제했는가"가 필요하다는 실무적 이유도 있다.
 *
 * <p>{@code REJECTED} 로 전이할 때 {@code rejection_reason_type} 이
 * 필수다(INV-11). 미입력 시 400(E-10).
 *
 * <p>상태 전이는 {@code GENERATED → ADOPTED} 또는 {@code GENERATED → REJECTED}
 * 이며, 채택과 기각 사이를 오가는 것도 확정 전이라면 허용된다. 확정된 청구
 * ({@code ClaimStatus.DECIDED})의 근거는 어떤 전이도 불가하다(INV-7).
 */
public enum EvidenceStatus {

    /** 생성·미검토. 룰 매칭 또는 모델 산출로 만들어진 직후의 상태. */
    GENERATED("생성 · 미검토"),

    /** 채택. 심사자가 판단 근거로 받아들인 상태. 설명문 초안의 재료가 된다. */
    ADOPTED("채택"),

    /** 기각. 심사자가 배제한 상태. 사유와 함께 보존되며 삭제되지 않는다. */
    REJECTED("기각");

    private final String label;

    EvidenceStatus(String label) {
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
