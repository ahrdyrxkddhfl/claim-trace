package com.claimtrace.dto;

import com.claimtrace.domain.enums.DisclosureLevel;
import com.claimtrace.domain.enums.Polarity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 심사자가 직접 추가하는 근거.
 *
 * <p>룰이 잡지 못했거나 모델이 산출하지 못한 판단 근거를 심사자가 넣는
 * 경로다. 이것이 없으면 이 시스템은 룰과 모델이 만든 근거만 쓰게 되고,
 * 사람이 보조수단으로서 독립적으로 판단한다는 전제가 성립하지 않는다.
 *
 * <p>공개 수준을 요청에서 받는다. 룰 근거와 AI 근거는 출처에서 기본값을
 * 물려받지만(D-4), 수동 근거는 물려받을 출처가 없어 심사자가 정한다.
 *
 * <p>{@code contentCustomer} 는 공개 수준이 고객용일 때만 필수다. 조건부
 * 필수라 Bean Validation 으로 표현되지 않으므로 서비스가 검사한다.
 *
 * @param polarity 근거가 가리키는 방향
 * @param disclosureLevel 공개 수준
 * @param contentInternal 심사자용 문구
 * @param contentCustomer 고객용 문구. 공개 수준이 CUSTOMER 이면 필수
 * @param documentId 근거가 된 서류 식별자. 같은 청구의 서류여야 한다
 * @param targetAmount 이 근거가 영향을 주는 금액(원)
 */
public record EvidenceCreateRequest(
        @NotNull(message = "근거의 방향이 필요합니다")
        Polarity polarity,

        @NotNull(message = "공개 수준이 필요합니다")
        DisclosureLevel disclosureLevel,

        @NotBlank(message = "심사자용 문구가 필요합니다")
        String contentInternal,

        String contentCustomer,

        Long documentId,

        @PositiveOrZero(message = "영향 금액은 0 이상이어야 합니다")
        Integer targetAmount) {
}
