package com.claimtrace.dto;

import java.time.LocalDateTime;

import com.claimtrace.domain.Review;
import com.claimtrace.domain.enums.ItemDecision;

/**
 * 항목 판정 응답.
 *
 * <p>{@code intervention} 이 이 응답의 요점이다. 판정을 저장했더니 개입
 * 기록이 함께 만들어졌다는 사실을 응답으로 돌려준다. 심사자가 개입을
 * 선언한 적이 없는데도 기록이 생겼다는 것이 D-7 의 동작이고, 화면 9 는
 * 이 값을 받아 "AI 권고를 뒤집었으며 개입으로 기록되었습니다"를 표시한다.
 *
 * @param id 판정 식별자
 * @param claimItemId 항목 식별자
 * @param decision 지급 판정
 * @param paidAmount 지급 결정액(원)
 * @param reason 판정 사유
 * @param isCurrent 현재 유효한 판정인지 여부
 * @param supersededBy 이 판정을 대체한 판정의 식별자. 유효한 판정이면 {@code null}
 * @param reviewerId 심사자 식별자
 * @param reviewerName 심사자 성명
 * @param decidedAt 판정 시각
 * @param intervention 이 판정과 함께 생성된 개입 기록. 없으면 {@code null}
 */
public record ReviewResponse(
        Long id,
        Long claimItemId,
        ItemDecision decision,
        Integer paidAmount,
        String reason,
        boolean isCurrent,
        Long supersededBy,
        Long reviewerId,
        String reviewerName,
        LocalDateTime decidedAt,
        InterventionResponse intervention) {

    /**
     * 엔티티를 응답으로 변환한다.
     *
     * <p>지연 로딩된 연관을 읽으므로 트랜잭션 안에서 호출해야 한다.
     *
     * @param review 변환할 판정
     * @param intervention 함께 생성된 개입 기록. 없으면 {@code null}
     * @return 판정 응답
     */
    public static ReviewResponse from(Review review, InterventionResponse intervention) {
        return new ReviewResponse(
                review.getId(),
                review.getClaimItem().getId(),
                review.getDecision(),
                review.getPaidAmount(),
                review.getReason(),
                review.isCurrent(),
                review.getSupersededBy() == null ? null : review.getSupersededBy().getId(),
                review.getReviewer().getId(),
                review.getReviewer().getName(),
                review.getDecidedAt(),
                intervention);
    }

    /**
     * 개입 기록 없이 엔티티를 응답으로 변환한다.
     *
     * @param review 변환할 판정
     * @return 판정 응답
     */
    public static ReviewResponse from(Review review) {
        return from(review, null);
    }
}
