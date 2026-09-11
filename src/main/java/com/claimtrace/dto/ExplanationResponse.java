package com.claimtrace.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.claimtrace.domain.Explanation;
import com.claimtrace.domain.enums.ExplanationStatus;
import com.claimtrace.domain.enums.ExplanationType;

/**
 * 설명서 응답.
 *
 * <p>{@code body} 는 초안 생성 시점에는 시스템이 조립한 문안이고, 심사자가
 * 화면 11 에서 수정한 뒤 확정하면 그 내용으로 대체된다. 초안이 곧 발급물이
 * 아니라는 점이 중요하다. {@code status} 가 {@code GENERATING} 에 머무는 동안은
 * 고객에게 아무것도 나가지 않는다.
 *
 * @param id 설명서 식별자
 * @param claimId 대상 청구 식별자. 전역 설명이면 {@code null}
 * @param type 설명 범위
 * @param status 처리 상태
 * @param modelName 대상 모델. 국소 설명이면 {@code null}
 * @param body 설명문 본문. 작성 전이면 {@code null}
 * @param fileUrl 발급 파일 주소. 발급 전이면 {@code null}
 * @param draftedBy 확정한 심사자 성명. 확정 전이면 {@code null}
 * @param requestedAt 신청 시각
 * @param dueDate 제공 기한. 국소 설명이면 반드시 존재한다(INV-8)
 * @param providedAt 발급 시각. 발급 전이면 {@code null}
 */
public record ExplanationResponse(
        Long id,
        Long claimId,
        ExplanationType type,
        ExplanationStatus status,
        String modelName,
        String body,
        String fileUrl,
        String draftedBy,
        LocalDateTime requestedAt,
        LocalDate dueDate,
        LocalDateTime providedAt) {

    /**
     * 엔티티를 응답으로 변환한다.
     *
     * <p>지연 로딩된 연관을 읽으므로 트랜잭션 안에서 호출해야 한다.
     *
     * @param explanation 변환할 설명서
     * @return 설명서 응답
     */
    public static ExplanationResponse from(Explanation explanation) {
        return new ExplanationResponse(
                explanation.getId(),
                explanation.getClaim() == null ? null : explanation.getClaim().getId(),
                explanation.getType(),
                explanation.getStatus(),
                explanation.getModelName(),
                explanation.getBody(),
                explanation.getFileUrl(),
                explanation.getDraftedBy() == null ? null : explanation.getDraftedBy().getName(),
                explanation.getRequestedAt(),
                explanation.getDueDate(),
                explanation.getProvidedAt());
    }
}
