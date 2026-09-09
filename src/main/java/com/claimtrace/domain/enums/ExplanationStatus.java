package com.claimtrace.domain.enums;

/**
 * 설명서의 처리 상태.
 *
 * <p>{@code REQUESTED → GENERATING → PROVIDED} 가 정상 흐름이고,
 * 기한을 넘기면 {@code OVERDUE} 로 표시된다(E-7).
 *
 * <p>{@code GENERATING} 에서 {@code PROVIDED} 로 가는 전이는 심사자가
 * 초안을 검토·확정해야 일어난다. 완전 자동 발급 경로는 두지 않는다.
 * 초안 생성만으로 고객에게 나가면 보조수단성 원칙이 요구하는 사람의
 * 최종 확인이 빠지기 때문이다.
 */
public enum ExplanationStatus {

    /** 신청 접수. 청구인이 설명서를 요청했고 아직 작성 전인 상태. */
    REQUESTED("신청 접수"),

    /** 작성중. 초안이 생성되어 심사자가 검토·수정하는 상태. */
    GENERATING("작성중"),

    /** 발급 완료. 심사자가 확정해 고객에게 노출된 상태. */
    PROVIDED("발급 완료"),

    /** 기한 초과. 제공 기한을 넘긴 상태. 화면 5 에 안내를 표시한다. */
    OVERDUE("기한 초과");

    private final String label;

    ExplanationStatus(String label) {
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
