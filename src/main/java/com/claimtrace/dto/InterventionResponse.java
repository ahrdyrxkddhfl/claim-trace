package com.claimtrace.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.claimtrace.domain.Intervention;
import com.claimtrace.domain.enums.ItemDecision;
import com.claimtrace.domain.enums.InterventionType;
import com.claimtrace.domain.enums.OverrideReasonType;

/**
 * 인적 개입 응답.
 *
 * <p>{@code recommendation} 에 뒤집힌 권고의 판정과 확률을 함께 싣는다.
 * 개입 기록만 보고도 "무엇을 무엇으로 뒤집었는가"를 알 수 있어야 하기
 * 때문이다(D-1). 이 값이 없으면 개입 이력을 볼 때마다 AI 권고 테이블을
 * 다시 조회해야 한다.
 *
 * @param id 개입 식별자
 * @param claimId 청구 식별자
 * @param claimItemId 항목 식별자. 오버라이드가 아니면 {@code null}
 * @param type 개입 유형
 * @param overrideReasonType 오버라이드 사유 유형. 오버라이드가 아니면 {@code null}
 * @param reason 개입 사유
 * @param actorId 행위자 식별자
 * @param actorName 행위자 성명
 * @param approved 승인 여부. 승인 절차가 없는 유형이면 {@code null}
 * @param approvedBy 승인·반려를 수행한 사용자 성명. 처리 전이면 {@code null}
 * @param approvedAt 승인·반려 시각. 처리 전이면 {@code null}
 * @param approvalNote 승인·반려 사유. 없으면 {@code null}
 * @param ruleCode 발동 규칙 코드. 규칙 발동이 아니면 {@code null}
 * @param recommendation 뒤집힌 AI 권고. 오버라이드가 아니면 {@code null}
 * @param occurredAt 발생 시각
 */
public record InterventionResponse(
        Long id,
        Long claimId,
        Long claimItemId,
        InterventionType type,
        OverrideReasonType overrideReasonType,
        String reason,
        Long actorId,
        String actorName,
        Boolean approved,
        String approvedBy,
        LocalDateTime approvedAt,
        String approvalNote,
        String ruleCode,
        RecommendationSnapshot recommendation,
        LocalDateTime occurredAt) {

    /**
     * 뒤집힌 AI 권고의 요약.
     *
     * @param decision 권고했던 판정
     * @param exclusionProbability 산출된 보상제외 확률
     */
    public record RecommendationSnapshot(ItemDecision decision, BigDecimal exclusionProbability) {
    }

    /**
     * 엔티티를 응답으로 변환한다.
     *
     * <p>지연 로딩된 연관을 읽으므로 트랜잭션 안에서 호출해야 한다.
     * 컨트롤러에서 부르면 영속성 컨텍스트가 이미 닫혀 예외가 난다.
     *
     * @param intervention 변환할 개입 기록
     * @return 개입 응답. 인자가 {@code null} 이면 {@code null}
     */
    public static InterventionResponse from(Intervention intervention) {
        if (intervention == null) {
            return null;
        }
        RecommendationSnapshot snapshot = intervention.getAiRecommendation() == null
                ? null
                : new RecommendationSnapshot(
                        intervention.getAiRecommendation().getRecommendation(),
                        intervention.getAiRecommendation().getExclusionProbability());

        return new InterventionResponse(
                intervention.getId(),
                intervention.getClaim().getId(),
                intervention.getClaimItem() == null ? null : intervention.getClaimItem().getId(),
                intervention.getType(),
                intervention.getOverrideReasonType(),
                intervention.getReason(),
                intervention.getActor().getId(),
                intervention.getActor().getName(),
                intervention.getApproved(),
                intervention.getApprovedBy() == null ? null : intervention.getApprovedBy().getName(),
                intervention.getApprovedAt(),
                intervention.getApprovalNote(),
                intervention.getInterventionRule() == null
                        ? null : intervention.getInterventionRule().getCode(),
                snapshot,
                intervention.getOccurredAt());
    }
}
