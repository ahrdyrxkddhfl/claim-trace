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

    @Test
    @DisplayName("심사자가 추가한 근거는 즉시 채택 상태다")
    void 심사자가_추가한_근거는_즉시_채택_상태다() throws Exception {
        createEvidence(ITEM_MANUAL_THERAPY, REVIEWER, """
                {"polarity":"POSITIVE","disclosureLevel":"CUSTOMER",
                 "contentInternal":"담당의 소견서에 치료 필요성이 명시되어 있다.",
                 "contentCustomer":"담당 의사 선생님의 소견서에서 치료가 필요했다는 점이 확인되었습니다.",
                 "targetAmount":120000}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.status").value("ADOPTED"))
                .andExpect(jsonPath("$.decidedBy").value("김영희"))
                .andExpect(jsonPath("$.rule").doesNotExist());
    }

    @Test
    @DisplayName("고객용 근거에 고객용 문구가 없으면 추가할 수 없다")
    void 고객용_근거에_고객용_문구가_없으면_추가할_수_없다() throws Exception {
        // 설계 문서가 규정하지 않았던 조건이다. 그대로 두면 설명문 생성 시
        // 조용히 걸러져, 심사자는 공개했다고 믿지만 고객 문서에는 없다.
        createEvidence(ITEM_MANUAL_THERAPY, REVIEWER, """
                {"polarity":"NEGATIVE","disclosureLevel":"CUSTOMER",
                 "contentInternal":"시행 간격이 과도하다."}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CUSTOMER_CONTENT_REQUIRED"));
    }

    @Test
    @DisplayName("내부용 근거는 고객용 문구 없이 추가할 수 있다")
    void 내부용_근거는_고객용_문구_없이_추가할_수_있다() throws Exception {
        createEvidence(ITEM_MANUAL_THERAPY, REVIEWER, """
                {"polarity":"NEGATIVE","disclosureLevel":"INTERNAL",
                 "contentInternal":"동일 의료기관의 유사 청구 이력이 확인된다."}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disclosureLevel").value("INTERNAL"))
                .andExpect(jsonPath("$.contentCustomer").doesNotExist());
    }

    @Test
    @DisplayName("다른 청구의 서류는 근거로 지목할 수 없다")
    void 다른_청구의_서류는_근거로_지목할_수_없다() throws Exception {
        // 서류 4는 확정된 청구 2에 속한다. 청구 1의 항목에 연결하면
        // 판정의 출처를 추적했을 때 존재하지 않는 경로가 나온다.
        createEvidence(ITEM_MANUAL_THERAPY, REVIEWER, """
                {"polarity":"POSITIVE","disclosureLevel":"INTERNAL",
                 "contentInternal":"타 청구 서류 연결 시도","documentId":4}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("INV-7 확정된 청구에는 근거를 추가할 수 없다")
    void 확정된_청구에는_근거를_추가할_수_없다() throws Exception {
        createEvidence(5L, REVIEWER, """
                {"polarity":"POSITIVE","disclosureLevel":"INTERNAL",
                 "contentInternal":"확정 후 근거 추가 시도"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.invariant").value("INV-7"));
    }
}
