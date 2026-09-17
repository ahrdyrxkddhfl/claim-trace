package com.claimtrace.domain.enums;

/**
 * 사내 사용자의 역할.
 *
 * <p>기술서 3장의 액터 정의에 대응한다. 심사자는 개별 청구를 판정하고,
 * 심사관리자는 개입 규칙을 관리하며 복수인 확인을 승인한다. 두 역할은
 * 직무 분리 원칙에 따라 겹치지 않는다. 판정을 수행한 심사자 본인은
 * 그 판정에 대한 승인 권한에서 제외된다(보조수단성 점검항목 ④).
 *
 * <p>로그인 성공 시 이 값에 따라 진입 화면이 갈린다. 심사자는 화면 8(심사 큐),
 * 심사관리자는 화면 13(개입 이력 모니터링)으로 이동한다.
 */
public enum UserRole {

    /** 심사자. 화면 8~11 을 사용하며 항목 판정과 근거 검토를 수행한다. */
    REVIEWER("심사자"),

    /** 심사관리자. 화면 12~14 를 사용하며 규칙 설정과 개입 승인을 수행한다. */
    REVIEW_MANAGER("심사관리자");

    private final String label;

    UserRole(String label) {
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
