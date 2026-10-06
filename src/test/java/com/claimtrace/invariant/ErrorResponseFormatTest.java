package com.claimtrace.invariant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 요청 형식 오류도 공통 오류 형식으로 나가는지 검증한다.
 *
 * <p>헤더 누락, 정의되지 않은 enum 값, 깨진 JSON 은 컨트롤러에 들어오기
 * 전에 Spring 이 거부한다. 이 경우에도 응답이 {@code code} 를 가진 같은
 * 형식이어야 클라이언트가 오류 처리를 한 갈래로 유지할 수 있다.
 *
 * <p>숫자가 아닌 식별자는 여기 없다. 변환 실패의 원인인
 * {@code NumberFormatException} 이 {@code IllegalArgumentException} 처리기에
 * 걸려 이미 공통 형식으로 나간다.
 */
@DisplayName("오류 응답 형식")
class ErrorResponseFormatTest extends InvariantTestSupport {

    @Test
    @DisplayName("행위자 헤더가 없으면 공통 형식의 400 으로 응답한다")
    void 행위자_헤더가_없으면_공통_형식으로_응답한다() throws Exception {
        mockMvc.perform(post("/claims/{claimId}/decision", CLAIM_IN_REVIEW))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details.header").value("X-Actor-Id"));
    }

    @Test
    @DisplayName("정의되지 않은 enum 값은 공통 형식의 400 으로 응답한다")
    void 정의되지_않은_enum_값은_공통_형식으로_응답한다() throws Exception {
        saveReview(ITEM_CONSULT, REVIEWER, """
                {"decision":"MAYBE","paidAmount":19200,"reason":"급여 진찰료로 지급한다."}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details.field").value("decision"))
                .andExpect(jsonPath("$.details.value").value("MAYBE"))
                .andExpect(jsonPath("$.details.allowed").value(
                        containsInAnyOrder("PAY", "PARTIAL", "DENY")));
    }

    @Test
    @DisplayName("JSON 형식이 깨진 본문은 공통 형식의 400 으로 응답한다")
    void JSON_형식이_깨진_본문은_공통_형식으로_응답한다() throws Exception {
        mockMvc.perform(post("/claim-items/{itemId}/reviews", ITEM_CONSULT)
                        .header("X-Actor-Id", REVIEWER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
