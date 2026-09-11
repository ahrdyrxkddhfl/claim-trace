package com.claimtrace.invariant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 근거 검토 경로의 불변조건 검증. INV-7 · 11.
 *
 * <p>기각이 삭제가 아니라는 점도 함께 확인한다(D-3). 기각된 근거가 응답에서
 * 사라지면 보존하는 의미가 없다.
 */
@DisplayName("근거 검토 — 불변조건")
class EvidenceInvariantTest extends InvariantTestSupport {

    @Test
    @DisplayName("INV-11 기각 사유 없이 근거를 기각할 수 없다")
    void 기각_사유_없이_근거를_기각할_수_없다() throws Exception {
        changeEvidenceStatus(EVIDENCE_THERAPY_RULE, REVIEWER, """
                {"status":"REJECTED"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REJECTION_REASON_REQUIRED"))
                .andExpect(jsonPath("$.invariant").value("INV-11"));
    }

    @Test
    @DisplayName("D-3 기각된 근거는 삭제되지 않고 사유와 함께 보존된다")
    void 기각된_근거는_삭제되지_않고_사유와_함께_보존된다() throws Exception {
        changeEvidenceStatus(EVIDENCE_THERAPY_RULE, REVIEWER, """
                {"status":"REJECTED","rejectionReasonType":"RULE_MISAPPLIED",
                 "rejectionNote":"1개월 내 10회 초과에 해당하지 않는다."}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReasonType").value("RULE_MISAPPLIED"))
                .andExpect(jsonPath("$.decidedBy").value("김영희"));

        // 기각한 근거가 목록에서 사라지지 않아야 한다. 사라지면 보존의 의미가 없다.
        findEvidences(ITEM_MANUAL_THERAPY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + EVIDENCE_THERAPY_RULE + ")].status")
                        .value("REJECTED"));
    }

    @Test
    @DisplayName("INV-7 확정된 청구의 근거는 변경할 수 없다")
    void 확정된_청구의_근거는_변경할_수_없다() throws Exception {
        changeEvidenceStatus(EVIDENCE_DECIDED_CLAIM, REVIEWER, """
                {"status":"REJECTED","rejectionReasonType":"DUPLICATE"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_ALREADY_DECIDED"))
                .andExpect(jsonPath("$.invariant").value("INV-7"));
    }

    @Test
    @DisplayName("INV-6 배정되지 않은 심사자는 근거를 검토할 수 없다")
    void 배정되지_않은_심사자는_근거를_검토할_수_없다() throws Exception {
        changeEvidenceStatus(EVIDENCE_THERAPY_RULE, OTHER_REVIEWER, """
                {"status":"ADOPTED"}
                """)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.invariant").value("INV-6"));
    }

    @Test
    @DisplayName("설계에 없는 전이는 거부된다 — 미검토로 되돌리기")
    void 미검토로_되돌리는_전이는_거부된다() throws Exception {
        changeEvidenceStatus(EVIDENCE_THERAPY_RULE, REVIEWER, """
                {"status":"GENERATED"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
