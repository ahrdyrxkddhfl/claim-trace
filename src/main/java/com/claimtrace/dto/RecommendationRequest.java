package com.claimtrace.dto;

import java.math.BigDecimal;
import java.util.List;

import com.claimtrace.domain.enums.ItemDecision;
import com.claimtrace.domain.enums.Polarity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * AI 모델의 권고 전달 요청.
 *
 * <p>항목별 보상제외 확률, 권고, 새플리 기여도를 한 번에 받는다. 모델은 청구
 * 단위로 추론하므로 요청도 청구 단위다.
 *
 * <p>{@code recommendation} 은 설계 명세에 없던 필드다. 명세는 확률과 임계값만
 * 받는데, 시드의 권고(0.61 → 일부지급, 0.82 → 부지급)는 임계값 하나로 계산되지
 * 않는다. 권고를 시스템이 만들어 내면 모델이 내리지 않은 판단이 모델의 권고로
 * 기록되므로, 모델이 보낸 값을 그대로 저장한다.
 *
 * <p>확률·임계값·기여도의 자릿수 제한은 컬럼 정밀도와 같다. 넘치는 값을
 * 형식 검증에서 거르지 않으면 저장 시점에 DB 오류로 나간다.
 *
 * @param modelName 모델 이름
 * @param modelVersion 모델 버전
 * @param threshold 모델이 쓴 판단 임계값
 * @param items 항목별 권고
 */
public record RecommendationRequest(
        @NotBlank(message = "모델 이름이 필요합니다")
        String modelName,

        @NotBlank(message = "모델 버전이 필요합니다")
        String modelVersion,

        @NotNull(message = "임계값이 필요합니다")
        @DecimalMin(value = "0", message = "임계값은 0 이상이어야 합니다")
        @DecimalMax(value = "1", message = "임계값은 1 이하여야 합니다")
        @Digits(integer = 1, fraction = 3, message = "임계값은 소수 셋째 자리까지입니다")
        BigDecimal threshold,

        @NotEmpty(message = "권고할 항목이 필요합니다")
        List<@Valid @NotNull Item> items) {

    /**
     * 항목 하나의 권고.
     *
     * @param claimItemId 대상 항목 식별자
     * @param exclusionProbability 보상제외 확률
     * @param recommendation 모델의 권고
     * @param contributions 새플리 기여도. 근거가 없으면 빈 배열
     */
    public record Item(
            @NotNull(message = "항목 식별자가 필요합니다")
            Long claimItemId,

            @NotNull(message = "보상제외 확률이 필요합니다")
            @DecimalMin(value = "0", message = "확률은 0 이상이어야 합니다")
            @DecimalMax(value = "1", message = "확률은 1 이하여야 합니다")
            @Digits(integer = 1, fraction = 3, message = "확률은 소수 셋째 자리까지입니다")
            BigDecimal exclusionProbability,

            @NotNull(message = "권고 값이 필요합니다")
            ItemDecision recommendation,

            @NotNull(message = "기여도 목록이 필요합니다")
            List<@Valid @NotNull Contribution> contributions) {
    }

    /**
     * 새플리 기여도 하나.
     *
     * @param feature 기여한 특성의 설명
     * @param value 기여도
     * @param polarity 기여 방향
     */
    public record Contribution(
            @NotBlank(message = "특성 설명이 필요합니다")
            String feature,

            @NotNull(message = "기여도 값이 필요합니다")
            @Digits(integer = 2, fraction = 3, message = "기여도는 소수 셋째 자리까지입니다")
            BigDecimal value,

            @NotNull(message = "기여 방향이 필요합니다")
            Polarity polarity) {
    }
}
