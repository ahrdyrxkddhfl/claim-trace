package com.claimtrace.invariant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 판정 확정 경로의 불변조건 검증. INV-4 · 10, 그리고 직무 분리.
 *
 * <p>확정은 청구 전체를 보아야 판단할 수 있는 조건들이 모이는 자리다.
 * 여기서 검증하는 두 조건은 항목 하나나 판정 하나만 보아서는 판별되지
 * 않으며, 그래서 판정 저장 시점으로 앞당길 수 없다.
 */
@DisplayName("판정 확정 — 불변조건")
class DecisionInvariantTest extends InvariantTestSupport {

    @Test
    @DisplayName("INV-10 미판정 항목이 남으면 확정할 수 없다")
    void 미판정_항목이_남으면_확정할_수_없다() throws Exception {
        // 항목 하나만 판정하고 확정을 시도한다. 나머지 셋이 남아 있다.
        saveReview(ITEM_CONSULT, REVIEWER, """
                {"decision":"PAY","paidAmount":19200,"reason":"부정 근거가 확인되지 않는다."}
                """);

        decide(CLAIM_IN_REVIEW, REVIEWER)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PENDING_ITEMS_EXIST"))
                .andExpect(jsonPath("$.invariant").value("INV-10"))
                .andExpect(jsonPath("$.details.pendingItemIds").isNotEmpty());
    }

    @Test
    @DisplayName("INV-4 정책이 요구한 승인 없이는 확정할 수 없다")
    void 정책이_요구한_승인_없이는_확정할_수_없다() throws Exception {
        reviewAllItemsFollowingAi();

        // 도수치료 항목이 P-07(비급여 · 30만원 이상)과 P-03(확률 0.8 이상)에
        // 걸린다. 두 정책의 조건은 코드가 아니라 DB 의 JSON 에 있다 (D-5).
        decide(CLAIM_IN_REVIEW, REVIEWER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DUAL_CHECK_REQUIRED"))
                .andExpect(jsonPath("$.invariant").value("INV-4"))
                .andExpect(jsonPath("$.details.policyCodes").isNotEmpty());
    }

    @Test
    @DisplayName("INV-4 판정한 심사자 본인은 승인할 수 없다")
    void 판정한_심사자_본인은_승인할_수_없다() throws Exception {
        reviewAllItemsFollowingAi();

        resolveDualCheck(CLAIM_IN_REVIEW, REVIEWER, """
                {"approved":true,"note":"본인 승인 시도"}
                """)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_APPROVAL_FORBIDDEN"))
                .andExpect(jsonPath("$.invariant").value("INV-4"));
    }

    @Test
    @DisplayName("INV-4 정책이 지정하지 않은 역할은 승인할 수 없다")
    void 정책이_지정하지_않은_역할은_승인할_수_없다() throws Exception {
        reviewAllItemsFollowingAi();

        // 이도현은 심사자다. 정책의 approverRoles 는 REVIEW_MANAGER 만 허용한다.
        resolveDualCheck(CLAIM_IN_REVIEW, OTHER_REVIEWER, """
                {"approved":true}
                """)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPROVER_ROLE_REQUIRED"))
                .andExpect(jsonPath("$.details.actorRole").value("REVIEWER"));
    }

    @Test
    @DisplayName("INV-1 승인에는 행위자와 시각이 기록된다")
    void 승인에는_행위자와_시각이_기록된다() throws Exception {
        reviewAllItemsFollowingAi();

        resolveDualCheck(CLAIM_IN_REVIEW, MANAGER, """
                {"approved":true,"note":"소견서 확인 결과 승인한다."}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].approved").value(true))
                .andExpect(jsonPath("$[0].approvedBy").value("박준호"))
                .andExpect(jsonPath("$[0].approvedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].approvalNote").value("소견서 확인 결과 승인한다."));
    }

    @Test
    @DisplayName("INV-4 반려된 개입도 확정을 막는다")
    void 반려된_개입도_확정을_막는다() throws Exception {
        reviewAllItemsFollowingAi();

        resolveDualCheck(CLAIM_IN_REVIEW, MANAGER, """
                {"approved":false,"note":"추가 서류 확인이 필요하다."}
                """).andExpect(status().isOk());

        decide(CLAIM_IN_REVIEW, REVIEWER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.invariant").value("INV-4"));
    }

    @Test
    @DisplayName("승인이 끝나면 확정되고 오버라이드 건수가 집계된다")
    void 승인이_끝나면_확정되고_오버라이드_건수가_집계된다() throws Exception {
        // 도수치료만 권고(DENY)를 뒤집는다. 오버라이드 1건이 기대값이다.
        saveReview(ITEM_CONSULT, REVIEWER, """
                {"decision":"PAY","paidAmount":19200,"reason":"부정 근거가 확인되지 않는다."}
                """);
        saveReview(ITEM_MANUAL_THERAPY, REVIEWER, """
                {"decision":"PARTIAL","paidAmount":240000,"reason":"한도 내 일부를 지급한다.",
                 "overrideReasonType":"TERMS_INTERPRETATION","overrideReason":"소견서로 요건이 충족된다."}
                """);
        saveReview(ITEM_SHOCKWAVE, REVIEWER, """
                {"decision":"PARTIAL","paidAmount":210000,"reason":"선행 보존치료 기록이 일부만 확인된다."}
                """);
        saveReview(ITEM_RADIOLOGY, REVIEWER, """
                {"decision":"PAY","paidAmount":144000,"reason":"부정 근거가 확인되지 않는다."}
                """);

        resolveDualCheck(CLAIM_IN_REVIEW, MANAGER, """
                {"approved":true,"note":"승인한다."}
                """).andExpect(status().isOk());

        decide(CLAIM_IN_REVIEW, REVIEWER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECIDED"))
                .andExpect(jsonPath("$.overrideCount").value(1))
                .andExpect(jsonPath("$.paidTotal").value(19200 + 240000 + 210000 + 144000));
    }

    @Test
    @DisplayName("INV-6 배정되지 않은 심사자는 확정할 수 없다")
    void 배정되지_않은_심사자는_확정할_수_없다() throws Exception {
        decide(CLAIM_IN_REVIEW, OTHER_REVIEWER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_ASSIGNED"))
                .andExpect(jsonPath("$.invariant").value("INV-6"));
    }
}
