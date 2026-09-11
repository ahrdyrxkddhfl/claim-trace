package com.claimtrace.invariant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.claimtrace.repository.ReviewRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 판정 저장 경로의 불변조건 검증. INV-1 · 2 · 3 · 6 · 7 · 12.
 *
 * <p>판정 저장은 우회 경로가 없는 단일 진입점이므로, 여기서 막히는 것은
 * 시스템 전체에서 막힌다.
 */
@DisplayName("판정 저장 — 불변조건")
class ReviewInvariantTest extends InvariantTestSupport {

    @Autowired
    private ReviewRepository reviewRepository;

    @Test
    @DisplayName("INV-1 실재하지 않는 행위자로는 판정할 수 없다")
    void 실재하지_않는_행위자로는_판정할_수_없다() throws Exception {
        saveReview(ITEM_CONSULT, 9999L, """
                {"decision":"PAY","paidAmount":19200,"reason":"사유"}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("INV-2 사유 없이 판정을 저장할 수 없다")
    void 사유_없이_판정을_저장할_수_없다() throws Exception {
        saveReview(ITEM_CONSULT, REVIEWER, """
                {"decision":"PAY","paidAmount":19200,"reason":"   "}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REVIEW_REASON_REQUIRED"))
                .andExpect(jsonPath("$.invariant").value("INV-2"));
    }

    @Test
    @DisplayName("INV-3 AI 권고와 다른 판정에 사유가 없으면 저장할 수 없다")
    void AI_권고와_다른_판정에_사유가_없으면_저장할_수_없다() throws Exception {
        // 도수치료의 AI 권고는 DENY 다. PARTIAL 은 권고를 뒤집는 판정이다.
        saveReview(ITEM_MANUAL_THERAPY, REVIEWER, """
                {"decision":"PARTIAL","paidAmount":240000,"reason":"한도 내 일부를 지급한다."}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OVERRIDE_REASON_REQUIRED"))
                .andExpect(jsonPath("$.invariant").value("INV-3"))
                .andExpect(jsonPath("$.details.recommendation").value("DENY"))
                .andExpect(jsonPath("$.details.decision").value("PARTIAL"));
    }

    @Test
    @DisplayName("INV-3 오버라이드 사유 유형만 있고 본문이 비면 저장할 수 없다")
    void 오버라이드_사유_본문이_비면_저장할_수_없다() throws Exception {
        saveReview(ITEM_MANUAL_THERAPY, REVIEWER, """
                {"decision":"PARTIAL","paidAmount":240000,"reason":"한도 내 일부를 지급한다.",
                 "overrideReasonType":"TERMS_INTERPRETATION","overrideReason":"  "}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invariant").value("INV-3"));
    }

    @Test
    @DisplayName("D-7 권고를 뒤집으면 선언 없이도 개입이 기록된다")
    void 권고를_뒤집으면_선언_없이도_개입이_기록된다() throws Exception {
        // 요청 어디에도 '개입'을 선언하는 필드가 없다. 시스템이 비교해 판별한다.
        saveReview(ITEM_MANUAL_THERAPY, REVIEWER, """
                {"decision":"PARTIAL","paidAmount":240000,"reason":"한도 내 일부를 지급한다.",
                 "overrideReasonType":"TERMS_INTERPRETATION","overrideReason":"소견서로 요건이 충족된다."}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.intervention.type").value("OVERRIDE"))
                .andExpect(jsonPath("$.intervention.recommendation.decision").value("DENY"));
    }

    @Test
    @DisplayName("D-7 권고와 같은 판정에는 개입이 기록되지 않는다")
    void 권고와_같은_판정에는_개입이_기록되지_않는다() throws Exception {
        saveReview(ITEM_CONSULT, REVIEWER, """
                {"decision":"PAY","paidAmount":19200,"reason":"부정 근거가 확인되지 않는다."}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.intervention").doesNotExist());
    }

    @Test
    @DisplayName("INV-6 배정되지 않은 심사자는 판정할 수 없다")
    void 배정되지_않은_심사자는_판정할_수_없다() throws Exception {
        saveReview(ITEM_CONSULT, OTHER_REVIEWER, """
                {"decision":"PAY","paidAmount":19200,"reason":"사유"}
                """)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_ASSIGNED"))
                .andExpect(jsonPath("$.invariant").value("INV-6"));
    }

    @Test
    @DisplayName("INV-7 확정된 청구의 항목은 다시 판정할 수 없다")
    void 확정된_청구의_항목은_다시_판정할_수_없다() throws Exception {
        // 청구 2 는 시드에서 이미 DECIDED 다. 항목 5 가 그 청구에 속한다.
        saveReview(5L, REVIEWER, """
                {"decision":"DENY","paidAmount":0,"reason":"재검토 결과 부지급으로 변경한다."}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_ALREADY_DECIDED"))
                .andExpect(jsonPath("$.invariant").value("INV-7"));
    }

    @Test
    @DisplayName("INV-12 재판정해도 현재 판정은 정확히 1건이다")
    void 재판정해도_현재_판정은_정확히_1건이다() throws Exception {
        saveReview(ITEM_CONSULT, REVIEWER, """
                {"decision":"PAY","paidAmount":19200,"reason":"최초 판정"}
                """).andExpect(status().isCreated());

        // 진찰료의 AI 권고는 PAY 다. 재판정도 PAY 로 둔다. 권고를 뒤집으면
        // INV-3 이 먼저 걸려 400 이 나므로, 여기서 검증하려는 INV-12 에
        // 도달하지 못한다.
        saveReview(ITEM_CONSULT, REVIEWER, """
                {"decision":"PAY","paidAmount":24000,"reason":"재검토 결과 전액 지급으로 정정한다."}
                """).andExpect(status().isCreated());

        // DB 가 아니라 서비스가 지키는 조건이다. H2 는 부분 UNIQUE 인덱스를
        // 지원하지 않으므로, 플래그 이관이 실제로 일어났는지를 개수로 확인한다.
        assertEquals(1, reviewRepository.countCurrentByItemId(ITEM_CONSULT),
                "항목당 현재 판정은 정확히 1건이어야 한다");
        assertEquals(2, reviewRepository.findHistoryByItemId(ITEM_CONSULT).size(),
                "대체된 판정도 이력으로 남아야 한다");
    }

    @Test
    @DisplayName("D-6 대체된 판정은 삭제되지 않고 대체 관계로 남는다")
    void 대체된_판정은_삭제되지_않고_대체_관계로_남는다() throws Exception {
        saveReview(ITEM_CONSULT, REVIEWER, """
                {"decision":"PAY","paidAmount":19200,"reason":"최초 판정"}
                """).andExpect(status().isCreated());
        saveReview(ITEM_CONSULT, REVIEWER, """
                {"decision":"PAY","paidAmount":24000,"reason":"재검토 결과 전액 지급으로 정정한다."}
                """).andExpect(status().isCreated());

        var superseded = reviewRepository.findHistoryByItemId(ITEM_CONSULT).stream()
                .filter(review -> !review.isCurrent())
                .findFirst();
        assertTrue(superseded.isPresent(), "대체된 판정이 남아 있어야 한다");
        assertNotNull(superseded.get().getSupersededBy(), "대체 관계가 연결되어야 한다");
        assertEquals("최초 판정", superseded.get().getReason(), "최초 판정의 사유가 보존되어야 한다");
    }
}
