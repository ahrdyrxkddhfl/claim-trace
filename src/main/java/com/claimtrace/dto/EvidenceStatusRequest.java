package com.claimtrace.dto;

import com.claimtrace.domain.enums.DisclosureLevel;
import com.claimtrace.domain.enums.EvidenceStatus;
import com.claimtrace.domain.enums.RejectionReasonType;

import jakarta.validation.constraints.NotNull;

/**
 * 근거 상태 변경 요청.
 *
 * <p>{@code rejectionReasonType} 이 필수인지 아닌지는 {@code status} 에 따라
 * 달라진다. 기각으로 전이할 때만 필요하다(INV-11). 한 필드의 필수 여부가
 * 다른 필드의 값에 달려 있으므로 Bean Validation 으로 표현할 수 없고,
 * 서비스가 검사해 {@code REJECTION_REASON_REQUIRED} 로 거부한다.
 *
 * <p>{@code disclosureLevel} 은 선택이다. 값이 있으면 상태 변경과 함께
 * 공개 수준도 바꾼다. 심사자가 화면 10 에서 채택과 공개 수준 조정을
 * 한 번에 하는 흐름에 맞춘 것이다.
 *
 * @param status 전이할 상태
 * @param rejectionReasonType 기각 사유 유형. 기각으로 전이할 때 필수(INV-11)
 * @param rejectionNote 기각 상세 사유. 선택
 * @param disclosureLevel 함께 변경할 공개 수준. 바꾸지 않으려면 {@code null}
 */
public record EvidenceStatusRequest(
        @NotNull(message = "전이할 상태가 필요합니다")
        EvidenceStatus status,

        RejectionReasonType rejectionReasonType,

        String rejectionNote,

        DisclosureLevel disclosureLevel) {
}
