package com.claimtrace.invariant;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.claimtrace.domain.InterventionRule;
import com.claimtrace.domain.enums.CoverageType;
import com.claimtrace.domain.enums.InterventionType;
import com.claimtrace.domain.enums.UserRole;
import com.claimtrace.exception.RuleDefinitionException;
import com.claimtrace.service.RuleEvaluator;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 개입 규칙 평가기 단위 테스트. D-5 의 검증.
 *
 * <p>Spring 컨텍스트를 띄우지 않는다. 평가기가 엔티티가 아니라 값
 * ({@link RuleEvaluator.Target})을 받도록 만든 이유가 여기서 드러난다.
 * 영속성 컨텍스트 없이 조건 조합을 직접 넣어 결과를 확인할 수 있다.
 *
 * <p>이 테스트가 증명하는 것은 "조건이 코드가 아니라 데이터에 있다"이다.
 * 아래 어느 테스트도 조건을 자바 코드로 표현하지 않는다. 전부 JSON 문자열을
 * 바꿔 넣고 결과가 달라지는지를 본다.
 */
@DisplayName("개입 규칙 평가기 — D-5")
class RuleEvaluatorTest {

    private final RuleEvaluator evaluator = new RuleEvaluator(JsonMapper.builder().build());

    /**
     * 조건 JSON 을 가진 규칙을 만든다.
     *
     * @param conditions 발동 조건 배열의 JSON 문자열
     * @return 활성 상태의 규칙
     */
    private InterventionRule rule(String conditions) {
        return InterventionRule.builder()
                .code("P-TEST")
                .name("테스트 규칙")
                .conditions(conditions)
                .requiredIntervention(InterventionType.DUAL_CHECK)
                .approverRoles("[\"REVIEW_MANAGER\"]")
                .active(true)
                .build();
    }

    /**
     * 평가용 항목 값을 만든다.
     *
     * @param amount 청구금액
     * @param type 담보 분류
     * @param probability 보상제외 확률
     * @return 평가 대상
     */
    private RuleEvaluator.Target target(int amount, CoverageType type, String probability) {
        return new RuleEvaluator.Target(
                1L, amount, type, probability == null ? null : new BigDecimal(probability));
    }

    @Test
    @DisplayName("모든 조건을 만족하는 항목이 있으면 발동한다")
    void 모든_조건을_만족하면_발동한다() {
        var p = rule("""
                [{"field":"coverageType","op":"eq","value":"DISEASE_UNCOVERED"},
                 {"field":"claimedAmount","op":"gte","value":300000}]
                """);
        assertTrue(evaluator.applies(p, List.of(
                target(480000, CoverageType.DISEASE_UNCOVERED, "0.82"))));
    }

    @Test
    @DisplayName("조건 하나라도 어긋나면 발동하지 않는다")
    void 조건_하나라도_어긋나면_발동하지_않는다() {
        var p = rule("""
                [{"field":"coverageType","op":"eq","value":"DISEASE_UNCOVERED"},
                 {"field":"claimedAmount","op":"gte","value":300000}]
                """);
        assertFalse(evaluator.applies(p, List.of(
                        target(480000, CoverageType.DISEASE_COVERED, "0.82"))),
                "금액은 넘지만 담보가 급여라 발동하지 않는다");
        assertFalse(evaluator.applies(p, List.of(
                        target(100000, CoverageType.DISEASE_UNCOVERED, "0.82"))),
                "담보는 맞지만 금액이 미달이라 발동하지 않는다");
    }

    @Test
    @DisplayName("항목 중 하나만 만족해도 청구에 발동한다")
    void 항목_중_하나만_만족해도_발동한다() {
        var p = rule("""
                [{"field":"claimedAmount","op":"gte","value":300000}]
                """);
        assertTrue(evaluator.applies(p, List.of(
                        target(24000, CoverageType.DISEASE_COVERED, "0.04"),
                        target(480000, CoverageType.DISEASE_UNCOVERED, "0.82"))),
                "소액 항목과 고액 항목이 섞여 있으면 고액 항목 하나로 발동한다");
    }

    @Test
    @DisplayName("확률이 없는 항목은 확률 조건을 만족하지 않는다")
    void 확률이_없는_항목은_확률_조건을_만족하지_않는다() {
        var p = rule("""
                [{"field":"exclusionProbability","op":"gte","value":0.8}]
                """);
        // 모델 권고가 아직 도착하지 않은 항목이다. 모르는 값을 통과로
        // 취급하면 권고가 늦는 항목이 통제를 빠져나간다.
        assertFalse(evaluator.applies(p, List.of(
                        target(480000, CoverageType.DISEASE_UNCOVERED, null))),
                "확률을 모르는 항목은 확률 조건을 통과하지 않는다");
    }

    @Test
    @DisplayName("확률 경계값은 gte 에서 포함된다")
    void 확률_경계값은_포함된다() {
        var p = rule("""
                [{"field":"exclusionProbability","op":"gte","value":0.8}]
                """);
        assertTrue(evaluator.applies(p, List.of(
                        target(1, CoverageType.DISEASE_UNCOVERED, "0.800"))),
                "gte 는 경계값을 포함한다");
        assertFalse(evaluator.applies(p, List.of(
                        target(1, CoverageType.DISEASE_UNCOVERED, "0.799"))),
                "경계 바로 아래는 발동하지 않는다");
    }

    @Test
    @DisplayName("조건만 바꾸면 코드 변경 없이 동작이 달라진다")
    void 조건만_바꾸면_동작이_달라진다() {
        var target = List.of(target(250000, CoverageType.DISEASE_UNCOVERED, "0.5"));

        assertFalse(evaluator.applies(
                        rule("[{\"field\":\"claimedAmount\",\"op\":\"gte\",\"value\":300000}]"), target),
                "30만원 기준에서는 25만원 항목이 걸리지 않는다");
        assertTrue(evaluator.applies(
                        rule("[{\"field\":\"claimedAmount\",\"op\":\"gte\",\"value\":200000}]"), target),
                "기준만 20만원으로 낮추면 같은 항목이 걸린다. 코드는 한 줄도 바뀌지 않았다");
    }

    @Test
    @DisplayName("조건이 비어 있으면 정의 오류다")
    void 조건이_비어_있으면_정의_오류다() {
        assertThrows(RuleDefinitionException.class, () -> evaluator.applies(rule("[]"),
                List.of(target(1, CoverageType.DISEASE_COVERED, "0.1"))));
    }

    @Test
    @DisplayName("지원하지 않는 필드는 정의 오류다")
    void 지원하지_않는_필드는_정의_오류다() {
        assertThrows(RuleDefinitionException.class, () -> evaluator.applies(
                rule("[{\"field\":\"hospitalName\",\"op\":\"eq\",\"value\":\"서울정형외과\"}]"),
                List.of(target(1, CoverageType.DISEASE_COVERED, "0.1"))));
    }

    @Test
    @DisplayName("담보 분류에는 크기 비교를 쓸 수 없다")
    void 담보_분류에는_크기_비교를_쓸_수_없다() {
        assertThrows(RuleDefinitionException.class, () -> evaluator.applies(
                rule("[{\"field\":\"coverageType\",\"op\":\"gte\",\"value\":\"DISEASE_UNCOVERED\"}]"),
                List.of(target(1, CoverageType.DISEASE_UNCOVERED, "0.1"))));
    }

    @Test
    @DisplayName("승인 권한도 데이터에서 읽는다")
    void 승인_권한도_데이터에서_읽는다() {
        var p = rule("[{\"field\":\"claimedAmount\",\"op\":\"gte\",\"value\":1}]");
        assertTrue(evaluator.canApprove(p, UserRole.REVIEW_MANAGER),
                "규칙이 지정한 역할은 승인할 수 있다");
        assertFalse(evaluator.canApprove(p, UserRole.REVIEWER),
                "지정되지 않은 역할은 승인할 수 없다");
    }
}
