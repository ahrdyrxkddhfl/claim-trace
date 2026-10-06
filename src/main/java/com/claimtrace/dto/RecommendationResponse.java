package com.claimtrace.dto;

import java.math.BigDecimal;

import com.claimtrace.domain.enums.ItemDecision;

/**
 * 항목 하나에 등록된 권고의 요약.
 *
 * @param claimItemId 대상 항목 식별자
 * @param recommendationId 새로 최신이 된 권고의 식별자
 * @param recommendation 권고 값
 * @param exclusionProbability 보상제외 확률
 * @param evidenceCount 함께 생성된 AI 근거 수
 */
public record RecommendationResponse(
        Long claimItemId,
        Long recommendationId,
        ItemDecision recommendation,
        BigDecimal exclusionProbability,
        int evidenceCount) {
}
