package com.claimtrace.invariant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.claimtrace.repository.AiRecommendationRepository;
import com.claimtrace.repository.InterventionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 권고 수신 경로 검증. INV-7 · 13, D-4.
 *
 * <p>권고는 판정의 재료일 뿐 판정이 아니다. 그래서 새 권고가 최신이 되는지와
 * 함께, 판정·개입을 만들지 않는지를 확인한다.
 */
@DisplayName("AI 권고 수신")
class RecommendationIntakeTest extends InvariantTestSupport {

    /** 청구 2(확정)의 진찰료 항목. */
    private static final long ITEM_DECIDED_CLAIM = 5L;

    @Autowired
    private AiRecommendationRepository aiRecommendationRepository;

    @Autowired
    private InterventionRepository interventionRepository;

    @Test
    @DisplayName("새 권고가 최신이 되고 이전 권고는 지우지 않고 최신에서 내린다")
    void 새_권고가_최신이_되고_이전_권고는_최신에서_내린다() throws Exception {
        // 진찰료의 시드 권고는 PAY 다. 모델이 DENY 로 바꿔 보낸다.
        submitRecommendations(CLAIM_IN_REVIEW, """
                {"modelName":"claim-risk","modelVersion":"v2.4","threshold":0.3,
                 "items":[{"claimItemId":1,"exclusionProbability":0.850,
                           "recommendation":"DENY","contributions":[]}]}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].claimItemId").value(ITEM_CONSULT))
                .andExpect(jsonPath("$[0].recommendation").value("DENY"));

        assertThat(aiRecommendationRepository.findLatestByItemId(ITEM_CONSULT))
                .hasValueSatisfying(latest -> assertThat(latest.getModelVersion()).isEqualTo("v2.4"));

        // 판정 저장이 새 권고와 비교하는지로 확인한다. PAY 는 이제 권고를 뒤집는 판정이다.
        saveReview(ITEM_CONSULT, REVIEWER, """
                {"decision":"PAY","paidAmount":19200,"reason":"급여 진찰료로 지급한다."}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OVERRIDE_REASON_REQUIRED"))
                .andExpect(jsonPath("$.details.recommendation").value("DENY"));
    }

    @Test
    @DisplayName("D-4 기여도마다 내부용·미검토 AI 근거가 생긴다")
    void 기여도마다_내부용_미검토_AI_근거가_생긴다() throws Exception {
        submitRecommendations(CLAIM_IN_REVIEW, """
                {"modelName":"claim-risk","modelVersion":"v2.4","threshold":0.3,
                 "items":[{"claimItemId":1,"exclusionProbability":0.420,"recommendation":"PARTIAL",
                           "contributions":[
                             {"feature":"동일 상병 30일 내 재청구","value":0.310,"polarity":"NEGATIVE"},
                             {"feature":"급여 항목","value":0.120,"polarity":"POSITIVE"}]}]}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].evidenceCount").value(2));

        findEvidences(ITEM_CONSULT, REVIEWER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.source == 'AI')]", hasSize(2)))
                .andExpect(jsonPath("$[?(@.source == 'AI' && @.status == 'GENERATED')]", hasSize(2)))
                .andExpect(jsonPath("$[?(@.source == 'AI' && @.disclosureLevel == 'INTERNAL')]", hasSize(2)))
                .andExpect(jsonPath("$[?(@.source == 'AI' && @.polarity == 'NEGATIVE')].contentInternal")
                        .value("동일 상병 30일 내 재청구. 기여도 0.310."));
    }

    @Test
    @DisplayName("INV-13 권고는 판정과 개입을 만들지 않는다")
    void 권고는_판정과_개입을_만들지_않는다() throws Exception {
        long interventionsBefore = interventionRepository.count();

        // 0.85 는 P-03 의 발동 조건(0.8 이상)에 해당한다. 그래도 여기서 개입을 만들지 않는다.
        submitRecommendations(CLAIM_IN_REVIEW, """
                {"modelName":"claim-risk","modelVersion":"v2.4","threshold":0.3,
                 "items":[{"claimItemId":1,"exclusionProbability":0.850,
                           "recommendation":"DENY","contributions":[]}]}
                """)
                .andExpect(status().isCreated());

        assertThat(interventionRepository.count()).isEqualTo(interventionsBefore);
        findReviews(ITEM_CONSULT, REVIEWER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("INV-7 확정된 청구에는 권고를 보낼 수 없다")
    void 확정된_청구에는_권고를_보낼_수_없다() throws Exception {
        submitRecommendations(CLAIM_DECIDED, """
                {"modelName":"claim-risk","modelVersion":"v2.4","threshold":0.3,
                 "items":[{"claimItemId":5,"exclusionProbability":0.900,
                           "recommendation":"DENY","contributions":[]}]}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_ALREADY_DECIDED"))
                .andExpect(jsonPath("$.invariant").value("INV-7"));

        assertThat(aiRecommendationRepository.findLatestByItemId(ITEM_DECIDED_CLAIM))
                .hasValueSatisfying(latest -> assertThat(latest.getModelVersion()).isEqualTo("v2.3"));
    }

    @Test
    @DisplayName("다른 청구의 항목에는 권고를 보낼 수 없다")
    void 다른_청구의_항목에는_권고를_보낼_수_없다() throws Exception {
        submitRecommendations(CLAIM_IN_REVIEW, """
                {"modelName":"claim-risk","modelVersion":"v2.4","threshold":0.3,
                 "items":[{"claimItemId":5,"exclusionProbability":0.100,
                           "recommendation":"PAY","contributions":[]}]}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("한 요청에 같은 항목이 두 번 오면 아무것도 바꾸지 않고 거부한다")
    void 같은_항목이_두_번_오면_거부한다() throws Exception {
        submitRecommendations(CLAIM_IN_REVIEW, """
                {"modelName":"claim-risk","modelVersion":"v2.4","threshold":0.3,
                 "items":[{"claimItemId":1,"exclusionProbability":0.100,"recommendation":"PAY","contributions":[]},
                          {"claimItemId":1,"exclusionProbability":0.900,"recommendation":"DENY","contributions":[]}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(aiRecommendationRepository.findLatestByItemId(ITEM_CONSULT))
                .hasValueSatisfying(latest -> assertThat(latest.getModelVersion()).isEqualTo("v2.3"));
    }

    @Test
    @DisplayName("1 을 넘는 확률은 형식 오류로 거부한다")
    void 범위를_넘는_확률은_거부한다() throws Exception {
        submitRecommendations(CLAIM_IN_REVIEW, """
                {"modelName":"claim-risk","modelVersion":"v2.4","threshold":0.3,
                 "items":[{"claimItemId":1,"exclusionProbability":1.200,
                           "recommendation":"DENY","contributions":[]}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
