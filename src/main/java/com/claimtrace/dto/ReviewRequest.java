package com.claimtrace.dto;

import com.claimtrace.domain.enums.ItemDecision;
import com.claimtrace.domain.enums.OverrideReasonType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 항목 판정 저장 요청.
 *
 * <p><b>{@code reason} 에 {@code @NotBlank} 를 붙이지 않은 것은 의도적이다.</b>
 * Bean Validation 이 걸러내면 응답이 {@code INVALID_REQUEST} 로 나가는데,
 * 설계는 판정 사유 누락을 E-1 · INV-2 로 규정했다. 형식 오류와 불변조건
 * 위반을 같은 코드로 내보내면 응답의 {@code invariant} 필드가 의미를 잃는다.
 *
 * <p>그래서 경계를 이렇게 나눈다. Bean Validation 은 <b>구조적 형식</b>만
 * 본다. 값이 아예 없거나 열거형에 없는 문자열이거나 음수인 경우다. 반면
 * <b>의미상의 규칙</b>, 즉 어떤 조건에서 어떤 값이 함께 있어야 하는지는
 * 서비스가 검사하고 불변조건 번호와 함께 거부한다.
 *
 * <p>{@code overrideReasonType} 과 {@code overrideReason} 이 필수인지는
 * 요청만 보고 알 수 없다. 이 항목의 최신 AI 권고를 읽어 판정과 비교해야
 * 정해진다(INV-3, D-7). 요청 스키마에 필수로 표시할 수 없는 조건이며,
 * 이것이 검증을 서비스에 두는 또 다른 이유다.
 *
 * @param decision 지급 판정
 * @param paidAmount 지급 결정액(원). 부지급이면 0
 * @param reason 판정 사유. 서비스가 공백 여부를 검사한다(INV-2)
 * @param overrideReasonType AI 권고와 다른 판정인 경우 필수(INV-3)
 * @param overrideReason 오버라이드 상세 사유. 유형과 함께 저장된다
 */
public record ReviewRequest(
        @NotNull(message = "판정 값이 필요합니다")
        ItemDecision decision,

        @NotNull(message = "지급 결정액이 필요합니다")
        @PositiveOrZero(message = "지급 결정액은 0 이상이어야 합니다")
        Integer paidAmount,

        String reason,

        OverrideReasonType overrideReasonType,

        String overrideReason) {
}
