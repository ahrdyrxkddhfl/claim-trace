package com.claimtrace.invariant;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 불변조건 테스트의 공통 기반.
 *
 * <p>이 테스트 묶음의 목적은 기능이 동작하는지 확인하는 것이 아니라,
 * <b>설계가 금지한 요청을 보냈을 때 지정된 예외가 반환되는지</b> 확인하는
 * 것이다. 기술서 7.1 검증 계획 ③에 "구현 단계로 넘어갈 경우 그대로 테스트
 * 케이스가 된다"고 적었고, 이 클래스들이 그것이다.
 *
 * <p>그래서 각 테스트는 HTTP 상태만 보지 않고 응답의 {@code code} 와
 * {@code invariant} 까지 확인한다. 400 이 났다는 사실보다 "어느 설계 규칙이
 * 이 요청을 막았는가"가 검증 대상이기 때문이다. 상태 코드만 보면 다른
 * 이유로 실패해도 테스트가 통과한다.
 *
 * <p>{@code @Transactional} 을 붙여 각 테스트 종료 시 롤백한다. H2 인메모리는
 * 기동 시 {@code data.sql} 로 한 번만 적재되므로, 롤백하지 않으면 앞 테스트가
 * 남긴 판정이 뒤 테스트의 전제를 바꾼다.
 *
 * <p>{@code MockMvc} 를 자동 구성 대신 웹 컨텍스트에서 직접 만든다.
 * 전역 예외 처리기와 메시지 컨버터가 실제 구성 그대로 적용되어야 응답
 * 본문의 {@code invariant} 필드를 검증할 수 있다.
 */
@SpringBootTest
@Transactional
abstract class InvariantTestSupport {

    /** 시드의 심사자 김영희. 청구 1 의 배정 심사자다. */
    protected static final long REVIEWER = 1L;

    /** 시드의 심사자 이도현. 청구 1 에 배정되지 않았다. INV-6 검증에 쓴다. */
    protected static final long OTHER_REVIEWER = 2L;

    /** 시드의 심사관리자 박준호. 복수인 확인 승인 권한을 가진다. */
    protected static final long MANAGER = 3L;

    /** 심사중 상태의 청구. 판정 0 건으로 시작한다. */
    protected static final long CLAIM_IN_REVIEW = 1L;

    /** 확정 완료 상태의 청구. INV-7 검증에 쓴다. */
    protected static final long CLAIM_DECIDED = 2L;

    /** 진찰료. AI 권고 PAY. */
    protected static final long ITEM_CONSULT = 1L;

    /** 도수치료. AI 권고 DENY, 보상제외 확률 0.82. 정책 두 개에 걸린다. */
    protected static final long ITEM_MANUAL_THERAPY = 2L;

    /** 체외충격파치료. AI 권고 PARTIAL. */
    protected static final long ITEM_SHOCKWAVE = 3L;

    /** 방사선단순영상진단. AI 권고 PAY. */
    protected static final long ITEM_RADIOLOGY = 4L;

    /** 도수치료의 룰 근거. 부정 · 고객용 · 미검토. */
    protected static final long EVIDENCE_THERAPY_RULE = 2L;

    /** 도수치료의 AI 기여도 근거. 부정 · 내부용 · 미검토. */
    protected static final long EVIDENCE_THERAPY_AI = 3L;

    /** 체외충격파의 룰 근거. 부정 · 고객용 · 미검토. */
    protected static final long EVIDENCE_SHOCKWAVE_RULE = 6L;

    /** 확정된 청구 2 의 근거. INV-7 검증에 쓴다. */
    protected static final long EVIDENCE_DECIDED_CLAIM = 9L;

    @Autowired
    private WebApplicationContext context;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    /**
     * 항목 판정을 저장한다.
     *
     * @param itemId 항목 식별자
     * @param actorId 행위자 식별자
     * @param body 요청 본문 JSON
     * @return 응답 검증을 이어갈 수 있는 결과
     * @throws Exception 요청 수행 중 오류
     */
    protected ResultActions saveReview(long itemId, long actorId, String body) throws Exception {
        return mockMvc.perform(post("/claim-items/{itemId}/reviews", itemId)
                .header("X-Actor-Id", actorId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /**
     * 근거 상태를 전이시킨다.
     *
     * @param evidenceId 근거 식별자
     * @param actorId 행위자 식별자
     * @param body 요청 본문 JSON
     * @return 응답 검증을 이어갈 수 있는 결과
     * @throws Exception 요청 수행 중 오류
     */
    protected ResultActions changeEvidenceStatus(long evidenceId, long actorId, String body)
            throws Exception {
        return mockMvc.perform(put("/evidences/{evidenceId}/status", evidenceId)
                .header("X-Actor-Id", actorId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /**
     * 청구 판정을 확정한다.
     *
     * @param claimId 청구 식별자
     * @param actorId 행위자 식별자
     * @return 응답 검증을 이어갈 수 있는 결과
     * @throws Exception 요청 수행 중 오류
     */
    protected ResultActions decide(long claimId, long actorId) throws Exception {
        return mockMvc.perform(post("/claims/{claimId}/decision", claimId)
                .header("X-Actor-Id", actorId));
    }

    /**
     * 복수인 확인을 승인하거나 반려한다.
     *
     * @param claimId 청구 식별자
     * @param actorId 행위자 식별자
     * @param body 요청 본문 JSON
     * @return 응답 검증을 이어갈 수 있는 결과
     * @throws Exception 요청 수행 중 오류
     */
    protected ResultActions resolveDualCheck(long claimId, long actorId, String body) throws Exception {
        return mockMvc.perform(put("/admin/claims/{claimId}/dual-check", claimId)
                .header("X-Actor-Id", actorId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /**
     * 고객 설명문 초안을 생성한다.
     *
     * @param claimId 청구 식별자
     * @param actorId 행위자 식별자
     * @return 응답 검증을 이어갈 수 있는 결과
     * @throws Exception 요청 수행 중 오류
     */
    protected ResultActions draftExplanation(long claimId, long actorId) throws Exception {
        return mockMvc.perform(post("/claims/{claimId}/explanations/draft", claimId)
                .header("X-Actor-Id", actorId));
    }

    /**
     * 항목의 근거 카드 목록을 조회한다.
     *
     * @param itemId 항목 식별자
     * @return 응답 검증을 이어갈 수 있는 결과
     * @throws Exception 요청 수행 중 오류
     */
    protected ResultActions findEvidences(long itemId) throws Exception {
        return mockMvc.perform(get("/claim-items/{itemId}/evidences", itemId));
    }

    /**
     * 청구 1 의 네 항목을 모두 AI 권고와 같게 판정한다.
     *
     * <p>확정과 설명문 검증의 전제를 만든다. 권고와 같은 판정이므로 오버라이드
     * 사유가 필요 없고, 개입 기록도 정책 발동분만 생성된다.
     *
     * @throws Exception 요청 수행 중 오류
     */
    protected void reviewAllItemsFollowingAi() throws Exception {
        saveReview(ITEM_CONSULT, REVIEWER,
                """
                {"decision":"PAY","paidAmount":19200,"reason":"급여 항목으로 자기부담률을 적용했다."}
                """);
        saveReview(ITEM_MANUAL_THERAPY, REVIEWER,
                """
                {"decision":"DENY","paidAmount":0,"reason":"의학적 타당성이 확인되지 않는다."}
                """);
        saveReview(ITEM_SHOCKWAVE, REVIEWER,
                """
                {"decision":"PARTIAL","paidAmount":210000,"reason":"선행 보존치료 기록이 일부만 확인된다."}
                """);
        saveReview(ITEM_RADIOLOGY, REVIEWER,
                """
                {"decision":"PAY","paidAmount":144000,"reason":"부정 근거가 확인되지 않는다."}
                """);
    }
}
