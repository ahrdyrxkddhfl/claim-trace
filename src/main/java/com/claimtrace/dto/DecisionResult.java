package com.claimtrace.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.claimtrace.domain.enums.ClaimStatus;
import com.claimtrace.domain.enums.ItemDecision;

/**
 * 청구 판정 확정 결과.
 *
 * <p>{@code overrideCount} 를 응답에 담는 것이 이 DTO 의 요점이다. 이 청구에서
 * AI 권고가 몇 번 뒤집혔는지를 확정 시점에 그대로 돌려준다. 보조수단성 ⑧이
 * 요구하는 "인적 개입 이행 결과의 기록"이 사후 조회용 로그가 아니라 업무
 * 흐름 안에서 집계 가능한 값이라는 것을 드러낸다.
 *
 * <p>{@code paidTotal} 은 항목별 지급 결정액의 합이다. 저장된 값이 아니라
 * 확정 시점에 현재 판정들로부터 계산한다. 합계 컬럼을 따로 두면 항목 판정이
 * 바뀔 때마다 갱신해야 하고, 갱신을 한 번 빠뜨리면 청구 화면의 총액과
 * 항목별 금액의 합이 어긋난다.
 *
 * @param claimId 청구 식별자
 * @param status 확정 후 상태
 * @param paidTotal 지급 결정액 총합(원)
 * @param decidedAt 확정 시각
 * @param itemDecisions 항목별 판정 결과
 * @param overrideCount 이 청구에서 발생한 오버라이드 건수
 */
public record DecisionResult(
        Long claimId,
        ClaimStatus status,
        Integer paidTotal,
        LocalDateTime decidedAt,
        List<ItemDecisionSummary> itemDecisions,
        long overrideCount) {

    /**
     * 항목 하나의 확정된 판정.
     *
     * @param claimItemId 항목 식별자
     * @param decision 지급 판정
     * @param paidAmount 지급 결정액(원)
     */
    public record ItemDecisionSummary(Long claimItemId, ItemDecision decision, Integer paidAmount) {
    }
}
