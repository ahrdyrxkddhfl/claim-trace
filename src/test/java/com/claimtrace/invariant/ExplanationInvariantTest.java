package com.claimtrace.invariant;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.claimtrace.domain.Explanation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 설명문 초안 경로의 불변조건 검증. INV-5 · 8 · 9.
 *
 * <p>INV-9 는 예외 코드가 없는 불변조건이다. 위반이 오류 응답이 아니라 정보
 * 유출로 나타나기 때문에, 거부되는지가 아니라 <b>본문에 내부용 내용이
 * 섞이지 않았는지</b>를 확인해야 한다.
 */
@DisplayName("설명문 초안 — 불변조건")
class ExplanationInvariantTest extends InvariantTestSupport {

    @Test
    @DisplayName("INV-5 부정 근거가 없으면 부지급 설명서를 만들 수 없다")
    void 부정_근거가_없으면_부지급_설명서를_만들_수_없다() throws Exception {
        reviewAllItemsFollowingAi();

        // 근거를 아무것도 채택하지 않은 상태다. 도수치료는 DENY 이므로
        // 고객용 부정 근거가 1건 이상 있어야 한다.
        draftExplanation(CLAIM_IN_REVIEW, REVIEWER)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NEGATIVE_EVIDENCE_REQUIRED"))
                .andExpect(jsonPath("$.invariant").value("INV-5"))
                .andExpect(jsonPath("$.details.customerNegativeCount").value(0));
    }

    @Test
    @DisplayName("INV-5 내부용 부정 근거만 채택해도 설명서를 만들 수 없다")
    void 내부용_부정_근거만_채택해도_설명서를_만들_수_없다() throws Exception {
        reviewAllItemsFollowingAi();

        // AI 기여도 근거는 부정이지만 공개 수준이 INTERNAL 이다 (D-4).
        changeEvidenceStatus(EVIDENCE_THERAPY_AI, REVIEWER, """
                {"status":"ADOPTED"}
                """).andExpect(status().isOk());

        draftExplanation(CLAIM_IN_REVIEW, REVIEWER)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invariant").value("INV-5"));
    }

    @Test
    @DisplayName("INV-9 초안 본문에 내부용 근거가 섞이지 않는다")
    void 초안_본문에_내부용_근거가_섞이지_않는다() throws Exception {
        reviewAllItemsFollowingAi();
        adoptCustomerNegativeEvidences();

        // 내부용 근거도 함께 채택해 둔다. 채택되었더라도 공개 수준이
        // INTERNAL 이면 본문에 들어가서는 안 된다.
        changeEvidenceStatus(EVIDENCE_THERAPY_AI, REVIEWER, """
                {"status":"ADOPTED"}
                """).andExpect(status().isOk());

        String response = draftExplanation(CLAIM_IN_REVIEW, REVIEWER)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("GENERATING"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(response.contains("도수치료"), "판정한 항목은 본문에 나와야 한다");
        assertFalse(response.contains("기여도"),
                "채택했더라도 공개 수준이 INTERNAL 인 근거는 본문에 들어가면 안 된다 (INV-9)");
        assertFalse(response.contains("상위 5퍼센트"),
                "모델 기여도 근거의 내부용 문구가 노출되면 안 된다 (INV-9)");
    }

    @Test
    @DisplayName("INV-8 제공 기한 없는 국소 설명서는 만들 수 없다")
    void 제공_기한_없는_국소_설명서는_만들_수_없다() {
        // 엔티티 수준의 검증이다. 조건부 NOT NULL 은 DB 제약으로 표현되지
        // 않으므로 생성 시점에 막는다.
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class, () -> Explanation.local(null, null));
        assertTrue(thrown.getMessage().contains("INV-8"),
                "어느 불변조건이 막았는지 예외 메시지에 남아야 한다");

        // 기한이 있으면 생성된다.
        Explanation.local(null, LocalDate.of(2026, 9, 15));
    }

    @Test
    @DisplayName("INV-6 배정되지 않은 심사자는 설명문을 만들 수 없다")
    void 배정되지_않은_심사자는_설명문을_만들_수_없다() throws Exception {
        draftExplanation(CLAIM_IN_REVIEW, OTHER_REVIEWER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.invariant").value("INV-6"));
    }

    /**
     * 부지급·일부지급 항목의 고객용 부정 근거를 채택한다.
     *
     * @throws Exception 요청 수행 중 오류
     */
    private void adoptCustomerNegativeEvidences() throws Exception {
        changeEvidenceStatus(EVIDENCE_THERAPY_RULE, REVIEWER, """
                {"status":"ADOPTED"}
                """).andExpect(status().isOk());
        changeEvidenceStatus(EVIDENCE_SHOCKWAVE_RULE, REVIEWER, """
                {"status":"ADOPTED"}
                """).andExpect(status().isOk());
    }
}
