package com.claimtrace.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 복수인 확인 승인·반려 요청.
 *
 * <p>{@code approved} 가 {@code false} 이면 반려다. 반려된 개입도 확정을
 * 막는다. 승인되지 않은 것은 마찬가지이고, 반려를 이유로 확정이 열리면
 * 심사관리자가 반대한 건이 그대로 지급되기 때문이다.
 *
 * <p>{@code note} 는 선택이지만 반려 시에는 사실상 필요하다. 다만 명세가
 * 필수로 규정하지 않았으므로 강제하지 않는다. 설계에 없는 제약을 구현이
 * 임의로 더하지 않는다는 원칙을 따른다.
 *
 * @param approved 승인이면 {@code true}, 반려이면 {@code false}
 * @param note 처리 사유. 반려 시 기재를 권장한다
 */
public record DualCheckRequest(
        @NotNull(message = "승인 여부가 필요합니다")
        Boolean approved,

        String note) {
}
