package com.claimtrace.exception;

import org.springframework.http.HttpStatus;

/**
 * 오류 코드. 기술서 4.4 의 예외 시나리오 표를 코드로 옮긴 것이다.
 *
 * <p>각 상수가 <b>코드 · 메시지 · 위반된 불변조건 · HTTP 상태</b> 네 가지를
 * 함께 들고 있다. 이렇게 묶는 것이 이 프로젝트의 성격에 맞다. 어떤 응답이
 * 어느 설계 규칙을 강제하는지가 API 응답에 그대로 드러나야 하고, 그 대응이
 * 여러 곳에 흩어지면 문서와 구현이 어긋난다.
 *
 * <p>{@code invariant} 필드가 응답 본문에 실린다. 일반적인 API 에는 없는
 * 항목인데, 이 시스템에서는 오류가 "요청이 잘못됐다"를 넘어 "설계가 금지한
 * 상태를 만들려 했다"를 뜻하기 때문에 어느 규칙인지 밝히는 편이 정확하다.
 *
 * <p><b>구현 범위</b> — E-7(설명서 기한 초과), E-8(청구인의 타인 청구 조회),
 * E-11(외부시스템의 판정 확정 시도)은 각각 고객 포털과 외부 연계에 속해
 * 구현 범위 밖이므로 상수를 두지 않았다. 설계에서 빠진 것이 아니라
 * 구현하지 않은 것이며, README 의 제외 범위에 함께 적는다.
 */
public enum ErrorCode {

    /** E-1 · INV-2 · 판정 사유 없이 판정을 저장하려 한 경우. */
    REVIEW_REASON_REQUIRED(
            "판정 사유를 입력해야 저장할 수 있습니다", "INV-2", HttpStatus.BAD_REQUEST),

    /** E-2 · INV-3 · AI 권고와 다른 판정에 오버라이드 사유가 없는 경우. */
    OVERRIDE_REASON_REQUIRED(
            "AI 권고와 다른 판정에는 오버라이드 사유가 필요합니다", "INV-3", HttpStatus.BAD_REQUEST),

    /** E-3 · INV-5 · 부지급·일부지급 항목에 고객용 부정 근거가 없는 경우. */
    NEGATIVE_EVIDENCE_REQUIRED(
            "부지급 항목에 고객용 부정 근거가 없어 설명서를 생성할 수 없습니다", "INV-5", HttpStatus.BAD_REQUEST),

    /** E-4 · INV-4 · 개입 정책에 걸린 청구를 승인 없이 확정하려 한 경우. */
    DUAL_CHECK_REQUIRED(
            "복수인 확인 승인이 완료되어야 확정할 수 있습니다", "INV-4", HttpStatus.FORBIDDEN),

    /** E-5 · INV-6 · 자신에게 배정되지 않은 청구에 접근한 경우. */
    CLAIM_NOT_ASSIGNED(
            "본인에게 배정된 청구가 아닙니다", "INV-6", HttpStatus.FORBIDDEN),

    /** E-6 · INV-7 · 확정된 청구의 근거나 판정을 변경하려 한 경우. */
    CLAIM_ALREADY_DECIDED(
            "확정된 청구는 변경할 수 없습니다", "INV-7", HttpStatus.CONFLICT),

    /** E-9 · INV-10 · 미판정 항목이 남은 상태로 확정하려 한 경우. */
    PENDING_ITEMS_EXIST(
            "판정되지 않은 항목이 있어 확정할 수 없습니다", "INV-10", HttpStatus.BAD_REQUEST),

    /** E-10 · INV-11 · 기각 사유 없이 근거를 기각하려 한 경우. */
    REJECTION_REASON_REQUIRED(
            "근거를 기각하려면 기각 사유를 선택해야 합니다", "INV-11", HttpStatus.BAD_REQUEST),

    /** E-12 · 대상 자원이 존재하지 않는 경우. 불변조건과 무관하다. */
    RESOURCE_NOT_FOUND(
            "대상을 찾을 수 없습니다", null, HttpStatus.NOT_FOUND),

    /** 요청 값 자체가 형식에 맞지 않는 경우. 불변조건과 무관하다. */
    INVALID_REQUEST(
            "요청 값이 올바르지 않습니다", null, HttpStatus.BAD_REQUEST),

    /**
     * 개입 정책의 발동 조건 정의가 잘못된 경우.
     *
     * <p>요청자의 잘못이 아니라 운영 데이터의 오류이므로 500 이다. 조건을
     * 해석할 수 없는 정책을 조용히 건너뛰지 않고 요청 전체를 실패시키는
     * 이유는, 그 정책이 규제가 요구하는 통제 수단이기 때문이다. 통제가
     * 적용되지 않은 채 확정이 성공하는 것이 오류 응답보다 훨씬 위험하다.
     */
    POLICY_DEFINITION_INVALID(
            "개입 정책의 발동 조건을 해석할 수 없습니다", null, HttpStatus.INTERNAL_SERVER_ERROR);

    private final String message;
    private final String invariant;
    private final HttpStatus status;

    ErrorCode(String message, String invariant, HttpStatus status) {
        this.message = message;
        this.invariant = invariant;
        this.status = status;
    }

    /**
     * 기본 오류 메시지를 반환한다.
     *
     * @return 사용자에게 보일 한국어 메시지
     */
    public String getMessage() {
        return message;
    }

    /**
     * 위반된 불변조건 번호를 반환한다.
     *
     * @return INV-n 형식의 번호. 불변조건과 무관한 오류이면 {@code null}
     */
    public String getInvariant() {
        return invariant;
    }

    /**
     * 이 오류에 대응하는 HTTP 상태를 반환한다.
     *
     * @return HTTP 상태
     */
    public HttpStatus getStatus() {
        return status;
    }
}
