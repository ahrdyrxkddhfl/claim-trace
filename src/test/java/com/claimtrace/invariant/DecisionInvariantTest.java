package com.claimtrace.invariant;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.claimtrace.domain.Intervention;
import com.claimtrace.domain.InterventionApproval;
import com.claimtrace.repository.InterventionApprovalRepository;
import com.claimtrace.repository.InterventionRepository;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Autowired
    private InterventionRepository interventionRepository;

    @Autowired
    private InterventionApprovalRepository interventionApprovalRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    @DisplayName("INV-4 규칙이 요구한 승인 없이는 확정할 수 없다")
    void 규칙이_요구한_승인_없이는_확정할_수_없다() throws Exception {
        reviewAllItemsFollowingAi();

        // 도수치료 항목이 P-07(비급여 · 30만원 이상)과 P-03(확률 0.8 이상)에
        // 걸린다. 두 규칙의 조건은 코드가 아니라 DB 의 JSON 에 있다 (D-5).
        decide(CLAIM_IN_REVIEW, REVIEWER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DUAL_CHECK_REQUIRED"))
                .andExpect(jsonPath("$.invariant").value("INV-4"))
                .andExpect(jsonPath("$.details.ruleCodes").isNotEmpty());
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
    @DisplayName("INV-4 규칙이 지정하지 않은 역할은 승인할 수 없다")
    void 규칙이_지정하지_않은_역할은_승인할_수_없다() throws Exception {
        reviewAllItemsFollowingAi();

        // 이도현은 심사자다. 규칙의 approverRoles 는 REVIEW_MANAGER 만 허용한다.
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
    @DisplayName("반려한 뒤 승인해도 반려 이력이 순서대로 남는다")
    void 반려한_뒤_승인해도_반려_이력이_순서대로_남는다() throws Exception {
        reviewAllItemsFollowingAi();

        // 심사관리자가 먼저 반려한다. 서류가 부족하다는 판단이다.
        resolveDualCheck(CLAIM_IN_REVIEW, MANAGER, """
                {"approved":false,"note":"추가 서류 확인이 필요하다."}
                """)
                .andExpect(status().isOk());

        // 반려된 개입은 blocksDecision() 이 참이라 승인 대기 목록에 그대로 남는다.
        // 보완 서류가 들어와 같은 엔드포인트로 승인하면 통과한다. 이 두 번째
        // 호출이 개입의 현재 상태를 덮어쓰는 지점이다.
        resolveDualCheck(CLAIM_IN_REVIEW, MANAGER, """
                {"approved":true,"note":"보완 서류를 확인해 승인한다."}
                """)
                .andExpect(status().isOk())
                // 이력 조회 엔드포인트가 없으므로 승인 응답에 실어 보낸다.
                .andExpect(jsonPath("$[0].approvals.length()").value(2))
                .andExpect(jsonPath("$[0].approvals[0].approved").value(false))
                .andExpect(jsonPath("$[0].approvals[0].note").value("추가 서류 확인이 필요하다."))
                .andExpect(jsonPath("$[0].approvals[1].approved").value(true))
                .andExpect(jsonPath("$[0].approvals[1].note").value("보완 서류를 확인해 승인한다."));

        // 판정은 대체되어도 이전 판정이 이력으로 남는다(D-6). 통제 승인도
        // "누가 무엇을 근거로 결정했는가"의 일부이므로 같은 보존이 필요하다.
        // 보조수단성 ⑧이 요구하는 것은 개입 사실의 기록이 아니라 그 내용의
        // 기록이므로, 반려 사유가 사라지면 통제가 성립하지 않는다.
        List<Intervention> interventions = interventionRepository.findAllByClaimId(CLAIM_IN_REVIEW);
        assertFalse(interventions.isEmpty(), "규칙이 발동해 개입이 기록되어 있어야 한다");

        for (Intervention intervention : interventions) {
            List<InterventionApproval> history = interventionApprovalRepository
                    .findAllByInterventionIds(List.of(intervention.getId()));

            assertEquals(2, history.size(),
                    "개입 " + intervention.getId() + " 의 처리 이력은 반려와 승인 두 건이어야 한다");

            InterventionApproval rejection = history.get(0);
            assertFalse(rejection.isApproved(), "첫 처리는 반려로 남아야 한다");
            assertEquals("추가 서류 확인이 필요하다.", rejection.getNote(), "반려 사유가 남아야 한다");
            assertEquals(MANAGER, rejection.getApprover().getId(), "반려한 행위자가 남아야 한다");
            assertNotNull(rejection.getApprovedAt(), "반려 시각이 남아야 한다");

            InterventionApproval approval = history.get(1);
            assertTrue(approval.isApproved(), "두 번째 처리는 승인으로 남아야 한다");
            assertEquals("보완 서류를 확인해 승인한다.", approval.getNote(), "승인 사유가 남아야 한다");
            assertEquals(MANAGER, approval.getApprover().getId(), "승인한 행위자가 남아야 한다");
            assertNotNull(approval.getApprovedAt(), "승인 시각이 남아야 한다");
        }
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
    @DisplayName("INV-4 승인 뒤 판정을 바꾸면 다시 승인받아야 확정할 수 있다")
    void 승인_뒤_판정을_바꾸면_다시_승인받아야_확정할_수_있다() throws Exception {
        // 도수치료를 PARTIAL 240,000원으로 판정한 상태에서 승인받는다.
        // 이 시점의 지급 총액은 613,200원이고, 심사관리자가 확인한 것은 이 금액이다.
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
                {"approved":true,"note":"240,000원 일부 지급을 승인한다."}
                """).andExpect(status().isOk());

        // 승인 뒤 심사자가 도수치료를 전액 지급으로 바꾼다. 지급 총액이
        // 853,200원이 되는데, 이 금액은 누구의 확인도 거치지 않았다.
        saveReview(ITEM_MANUAL_THERAPY, REVIEWER, """
                {"decision":"PAY","paidAmount":480000,"reason":"추가 소견서로 전액 지급한다.",
                 "overrideReasonType":"TERMS_INTERPRETATION","overrideReason":"추가 소견서로 요건이 충족된다."}
                """).andExpect(status().isCreated());

        // 승인은 특정 판정에 대한 확인이므로, 판정이 바뀌면 다시 받아야 한다.
        decide(CLAIM_IN_REVIEW, REVIEWER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DUAL_CHECK_REQUIRED"))
                .andExpect(jsonPath("$.invariant").value("INV-4"))
                .andExpect(jsonPath("$.details.ruleCodes", containsInAnyOrder("P-07", "P-03")));

        // 다시 승인하면 바뀐 금액으로 확정된다.
        resolveDualCheck(CLAIM_IN_REVIEW, MANAGER, """
                {"approved":true,"note":"전액 지급을 승인한다."}
                """).andExpect(status().isOk());

        decide(CLAIM_IN_REVIEW, REVIEWER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidTotal").value(19200 + 480000 + 210000 + 144000));

        // 첫 승인은 지워지지 않는다. 그 승인이 240,000원 판정에 대한 것이었다는
        // 사실도 "누가 무엇을 확인했는가"의 일부다.
        List<Intervention> required = interventionRepository.findAllByClaimId(CLAIM_IN_REVIEW).stream()
                .filter(intervention -> intervention.getInterventionRule() != null)
                .toList();
        assertEquals(4, required.size(), "규칙 두 개에 대해 첫 승인분과 재승인분이 각각 남아야 한다");
        required.forEach(intervention -> assertEquals(Boolean.TRUE, intervention.getApproved(),
                "개입 " + intervention.getId() + " 은 승인된 상태여야 한다"));
    }

    @Test
    @DisplayName("판정 뒤에 도착한 권고는 확정 전에 평가되지 않는다")
    void 판정_뒤에_도착한_권고는_확정_전에_평가되지_않는다() throws Exception {
        // 판정을 저장하는 시점에는 도수치료의 권고가 아직 없다. 모델이
        // 재처리 중인 상태를 흉내 낸다. 권고를 넣고 내리는 경로가 구현
        // 범위에 없어 여기서는 직접 조작한다.
        jdbcTemplate.update(
                "UPDATE ai_recommendations SET is_latest = FALSE WHERE claim_item_id = ?",
                ITEM_MANUAL_THERAPY);

        // 규칙 평가는 이 시점에만 일어난다. 도수치료의 확률 0.82 는
        // P-03(확률 0.8 이상)의 유일한 발동 근거인데 지금은 보이지 않으므로
        // P-03 은 기록되지 않는다. 비급여 고액이라는 다른 근거가 있는 P-07 만
        // 남는다.
        reviewAllItemsFollowingAi();

        // 권고가 뒤늦게 도착한다. 확률 0.82 가 다시 최신이 되었고, 이 값이면
        // P-03 이 발동해야 한다.
        jdbcTemplate.update(
                "UPDATE ai_recommendations SET is_latest = TRUE WHERE claim_item_id = ?",
                ITEM_MANUAL_THERAPY);

        // 그러나 확정은 규칙을 다시 평가하지 않고 판정 당시 기록된 개입만
        // 본다. 그래서 P-03 은 평가된 적 없는 채로 남고 P-07 만 확정을 막는다.
        //
        // 이 단언은 개선된 동작이 아니라 현재 구현의 한계를 고정한 것이다.
        // README 11 장의 조치(규칙 버전 고정 → 확정 시점 재평가 → 트랜잭션
        // 분리)가 들어오면 기대값을 "P-07", "P-03" 으로 바꾼다.
        decide(CLAIM_IN_REVIEW, REVIEWER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DUAL_CHECK_REQUIRED"))
                .andExpect(jsonPath("$.invariant").value("INV-4"))
                .andExpect(jsonPath("$.details.ruleCodes", containsInAnyOrder("P-07")));
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
