package com.claimtrace.invariant;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 심사 전체 흐름 통합 검증.
 *
 * <p>다른 테스트 묶음과 목적이 다르다. 불변조건 테스트가 <b>금지된 요청이
 * 막히는지</b>를 보는 반면, 이 테스트는 <b>정상 경로가 끝까지 도는지</b>를
 * 본다. 각 규칙이 개별적으로 동작해도 전체를 이어 붙였을 때 막히는 지점이
 * 있을 수 있고, 그것은 규칙 하나를 보는 테스트로는 드러나지 않는다.
 *
 * <p>검증하는 흐름은 화면 9 부터 화면 11 까지의 심사자 업무 그대로다.
 *
 * <ol>
 *   <li>항목 4건을 판정한다. 그중 하나는 AI 권고를 뒤집어 개입이 기록된다</li>
 *   <li>확정을 시도하면 개입 규칙이 요구한 승인이 없어 거부된다 (INV-4)</li>
 *   <li>심사관리자가 복수인 확인을 승인한다</li>
 *   <li>확정에 성공하고 지급 총액과 오버라이드 건수가 집계된다</li>
 *   <li>확정된 청구의 근거는 더 이상 변경되지 않는다 (INV-7)</li>
 * </ol>
 *
 * <p>설명문 초안은 별도 메서드로 둔다. 초안 생성은 근거 채택을 전제로 하고,
 * 근거는 확정 전에만 손댈 수 있어 확정 전 시점에서 검증해야 하기 때문이다.
 *
 * <p>응답 필드 값을 함께 확인한다. 상태 코드만 보면 흐름이 이어졌다는 것만
 * 알 수 있고, 화면이 실제로 표시할 값이 맞게 계산되었는지는 알 수 없다.
 */
@DisplayName("심사 전체 흐름")
class ClaimWorkflowIntegrationTest extends InvariantTestSupport {

    /** 진찰료 지급액. 급여 24,000원에 자기부담률 20퍼센트를 적용한 값이다. */
    private static final int PAID_CONSULT = 19200;

    /** 도수치료 지급액. AI 권고(DENY)를 뒤집은 일부지급이다. */
    private static final int PAID_THERAPY = 240000;

    /** 체외충격파치료 지급액. */
    private static final int PAID_SHOCKWAVE = 210000;

    /** 방사선단순영상진단 지급액. */
    private static final int PAID_RADIOLOGY = 144000;

    @Test
    @DisplayName("판정부터 확정까지 이어지고 확정 후에는 근거가 잠긴다")
    void 판정부터_확정까지_이어진다() throws Exception {
        reviewFourItemsWithOneOverride();

        // 2. 개입 규칙이 요구한 승인이 없어 확정이 거부된다.
        //    조건은 코드가 아니라 intervention_policies 의 JSON 에 있다 (D-5).
        decide(CLAIM_IN_REVIEW, REVIEWER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DUAL_CHECK_REQUIRED"))
                .andExpect(jsonPath("$.invariant").value("INV-4"));

        // 3. 심사관리자가 승인한다. 판정자 본인은 승인할 수 없다.
        resolveDualCheck(CLAIM_IN_REVIEW, MANAGER, """
                {"approved":true,"note":"소견서 확인 결과 의학적 타당성이 인정되어 승인한다."}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].approved").value(true))
                .andExpect(jsonPath("$[0].approvedBy").value("박준호"));

        // 4. 확정된다. 지급 총액과 오버라이드 건수는 저장된 값이 아니라
        //    현재 판정과 개입 이력으로부터 계산된 값이다.
        decide(CLAIM_IN_REVIEW, REVIEWER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECIDED"))
                .andExpect(jsonPath("$.paidTotal").value(
                        PAID_CONSULT + PAID_THERAPY + PAID_SHOCKWAVE + PAID_RADIOLOGY))
                .andExpect(jsonPath("$.overrideCount").value(1))
                .andExpect(jsonPath("$.itemDecisions.length()").value(4));

        // 5. 확정 이후에는 근거도 판정도 변경되지 않는다.
        changeEvidenceStatus(EVIDENCE_THERAPY_RULE, REVIEWER, """
                {"status":"REJECTED","rejectionReasonType":"DUPLICATE"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.invariant").value("INV-7"));

        saveReview(ITEM_CONSULT, REVIEWER, """
                {"decision":"DENY","paidAmount":0,"reason":"확정 후 번복 시도"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.invariant").value("INV-7"));
    }

    @Test
    @DisplayName("근거를 채택하면 그 문구가 설명문 초안에 그대로 나타난다")
    void 채택한_근거가_설명문_초안에_나타난다() throws Exception {
        reviewFourItemsWithOneOverride();

        // 고객용 부정 근거를 채택하기 전에는 초안을 만들 수 없다 (INV-5).
        draftExplanation(CLAIM_IN_REVIEW, REVIEWER)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invariant").value("INV-5"));

        changeEvidenceStatus(EVIDENCE_THERAPY_RULE, REVIEWER, """
                {"status":"ADOPTED"}
                """).andExpect(status().isOk());
        changeEvidenceStatus(EVIDENCE_SHOCKWAVE_RULE, REVIEWER, """
                {"status":"ADOPTED"}
                """).andExpect(status().isOk());

        String response = draftExplanation(CLAIM_IN_REVIEW, REVIEWER)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("GENERATING"))
                .andExpect(jsonPath("$.dueDate").value("2026-09-15"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // 채택한 근거의 고객용 문구가 본문에 들어간다. 심사자가 화면 10 에서
        // 무엇을 채택했는지가 고객이 받는 문서에 그대로 반영된다는 뜻이다.
        assertTrue(response.contains("한 달 안에 도수치료를"),
                "채택한 도수치료 근거의 고객용 문구가 본문에 있어야 한다");
        assertTrue(response.contains("체외충격파치료는"),
                "채택한 체외충격파 근거의 고객용 문구가 본문에 있어야 한다");

        // 항목 4건이 모두 설명된다. 일부만 설명하는 문서는 고객이 받아서는
        // 안 되므로, 미판정 항목이 있으면 초안 생성 자체가 거부된다.
        assertTrue(response.contains("진찰료"), "진찰료 항목이 본문에 있어야 한다");
        assertTrue(response.contains("방사선단순영상진단"), "방사선 항목이 본문에 있어야 한다");

        // 내부용 근거는 채택 여부와 무관하게 본문에 들어가지 않는다 (INV-9).
        assertFalse(response.contains("기여도"), "모델 기여도가 고객 문서에 노출되면 안 된다");
    }

    @Test
    @DisplayName("기각한 근거는 설명문에 쓰이지 않지만 목록에는 남는다")
    void 기각한_근거는_설명문에_쓰이지_않지만_목록에는_남는다() throws Exception {
        reviewFourItemsWithOneOverride();

        // 도수치료의 룰 근거를 기각하고 다른 고객용 부정 근거가 없으면
        // 초안을 만들 수 없다. 기각은 "검토했으나 배제했다"이므로
        // 설명문의 재료가 되지 않는다.
        changeEvidenceStatus(EVIDENCE_THERAPY_RULE, REVIEWER, """
                {"status":"REJECTED","rejectionReasonType":"RULE_MISAPPLIED",
                 "rejectionNote":"1개월 내 10회 초과에 해당하지 않는다."}
                """).andExpect(status().isOk());

        draftExplanation(CLAIM_IN_REVIEW, REVIEWER)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invariant").value("INV-5"));

        // 그러나 근거 자체는 사유와 함께 보존된다 (D-3). 이의제기 재검토 시
        // "무엇을 검토했고 왜 배제했는가"에 답할 수 있어야 한다.
        findEvidences(ITEM_MANUAL_THERAPY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + EVIDENCE_THERAPY_RULE + ")].status")
                        .value("REJECTED"))
                .andExpect(jsonPath("$[?(@.id == " + EVIDENCE_THERAPY_RULE + ")].rejectionReasonType")
                        .value("RULE_MISAPPLIED"));
    }

    /**
     * 항목 4건을 판정한다. 도수치료만 AI 권고를 뒤집는다.
     *
     * <p>권고를 뒤집는 판정에는 오버라이드 사유가 필요하고, 저장과 같은
     * 트랜잭션에서 개입이 기록된다. 심사자가 개입을 선언하는 필드는 요청
     * 어디에도 없다(D-7).
     *
     * @throws Exception 요청 수행 중 오류
     */
    private void reviewFourItemsWithOneOverride() throws Exception {
        saveReview(ITEM_CONSULT, REVIEWER, """
                {"decision":"PAY","paidAmount":19200,
                 "reason":"급여 항목으로 자기부담률 20퍼센트를 적용했다."}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.intervention").doesNotExist());

        saveReview(ITEM_MANUAL_THERAPY, REVIEWER, """
                {"decision":"PARTIAL","paidAmount":240000,
                 "reason":"소견서로 의학적 타당성이 확인되어 한도 내 일부를 지급한다.",
                 "overrideReasonType":"TERMS_INTERPRETATION",
                 "overrideReason":"약관 제12조 3항의 확인 요건이 소견서로 충족된다고 판단했다."}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.intervention.type").value("OVERRIDE"))
                .andExpect(jsonPath("$.intervention.recommendation.decision").value("DENY"))
                .andExpect(jsonPath("$.intervention.actorName").value("김영희"));

        saveReview(ITEM_SHOCKWAVE, REVIEWER, """
                {"decision":"PARTIAL","paidAmount":210000,
                 "reason":"선행 보존치료 기록이 일부만 확인되어 한도 내에서 일부 지급한다."}
                """).andExpect(status().isCreated());

        saveReview(ITEM_RADIOLOGY, REVIEWER, """
                {"decision":"PAY","paidAmount":144000,
                 "reason":"급여 항목으로 부정 근거가 확인되지 않는다."}
                """).andExpect(status().isCreated());
    }
}
