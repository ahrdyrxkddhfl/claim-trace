package com.claimtrace.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.claimtrace.domain.InterventionPolicy;
import com.claimtrace.domain.enums.CoverageType;
import com.claimtrace.exception.PolicyDefinitionException;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 개입 정책의 발동 조건을 평가한다. D-5 가 코드로 성립하는 자리.
 *
 * <p>설계는 복수인 확인 조건을 코드에 하드코딩하는 대안을 기각했다. 보조수단성
 * 점검항목 ⑤가 "인적 개입 방식을 적용하는 기준이 <b>문서화</b>되어 있는가"를
 * 묻는데, 코드에 묻힌 조건은 심사관리자가 확인할 수도 변경할 수도 없기
 * 때문이다. 그 결정이 실제로 지켜지려면 조건을 DB 에서 읽어 평가하는 코드가
 * 있어야 한다. 이 클래스가 그것이다.
 *
 * <p><b>지원 범위를 좁힌 것은 의도적이다.</b> 필드는 {@code claimedAmount},
 * {@code exclusionProbability}, {@code coverageType} 세 가지, 연산자는
 * {@code gte}, {@code lte}, {@code eq} 세 가지다. 기술서와 화면에 등장하는
 * 정책은 이 조합으로 전부 표현된다. 범용 규칙 엔진을 만드는 것은 이
 * 시스템이 증명하려는 명제와 무관하고, 넓힐 때마다 검증할 조합이 늘어난다.
 * 새 필드를 더하는 일은 {@link #matches} 의 분기 하나를 추가하는 것이다.
 *
 * <p><b>조건 형식</b> — 조건 객체의 배열이며 모든 조건이 AND 로 결합된다.
 *
 * <pre>
 * [
 *   {"field": "coverageType",  "op": "eq",  "value": "DISEASE_UNCOVERED"},
 *   {"field": "claimedAmount", "op": "gte", "value": 300000}
 * ]
 * </pre>
 *
 * <p><b>정책은 항목 단위로 평가되고 청구 단위로 발동한다.</b> 청구 안의 어느
 * 항목 하나라도 모든 조건을 만족하면 그 청구에 정책이 발동한다. 위 예시의
 * "비급여 고액 항목"이 뜻하는 바가 그것이다. 청구 전체의 합계로 판단하면
 * 소액 항목 여러 건이 모여 고액이 된 청구까지 통제 대상이 되어, 정책의
 * 이름과 동작이 어긋난다.
 *
 * <p><b>엔티티가 아니라 {@link Target} 을 받는다.</b> 평가에 필요한 것은 세
 * 값뿐이고, JPA 엔티티를 직접 받으면 이 클래스를 시험하기 위해 영속성
 * 컨텍스트가 필요해진다. 지금 형태는 인자를 넣으면 결과가 나오는 순수
 * 함수라 단위 테스트로 조합을 전부 훑을 수 있다.
 */
@Component
public class PolicyEvaluator {

    /** 정책 조건이 참조할 수 있는 필드. 여기 없는 이름은 정의 오류다. */
    private static final String FIELD_CLAIMED_AMOUNT = "claimedAmount";
    private static final String FIELD_EXCLUSION_PROBABILITY = "exclusionProbability";
    private static final String FIELD_COVERAGE_TYPE = "coverageType";

    /** 지원하는 연산자. */
    private static final String OP_GTE = "gte";
    private static final String OP_LTE = "lte";
    private static final String OP_EQ = "eq";

    private final JsonMapper jsonMapper;

    /**
     * 의존성을 주입받는다.
     *
     * <p>Spring Boot 4 가 자동 구성하는 빈은 {@code JsonMapper} 다. Jackson 3
     * 으로 넘어가면서 패키지가 {@code tools.jackson} 으로 바뀌었고,
     * {@code ObjectMapper} 를 주입받으면 자동 구성된 매퍼가 아닌 다른
     * 인스턴스가 들어올 수 있다.
     *
     * @param jsonMapper JSON 파서
     */
    public PolicyEvaluator(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * 정책 평가에 필요한 항목의 값.
     *
     * <p>{@code exclusionProbability} 는 {@code null} 일 수 있다. 모델이 아직
     * 권고를 내놓지 않은 항목이 그렇다. 이 경우 확률을 참조하는 조건은
     * 만족하지 않는 것으로 본다. 값을 모르는 것을 조건 통과로 취급하면
     * 권고가 늦게 도착하는 항목이 통제를 빠져나간다.
     *
     * @param claimItemId 항목 식별자
     * @param claimedAmount 청구금액(원)
     * @param coverageType 담보 분류
     * @param exclusionProbability 최신 권고의 보상제외 확률. 권고가 없으면 {@code null}
     */
    public record Target(
            Long claimItemId,
            Integer claimedAmount,
            CoverageType coverageType,
            BigDecimal exclusionProbability) {
    }

    /**
     * 주어진 항목들에 발동하는 정책을 골라낸다.
     *
     * @param policies 평가 대상 정책. 활성 정책만 넘긴다
     * @param targets 청구에 속한 항목들의 평가용 값
     * @return 발동하는 정책 목록. 없으면 빈 목록
     * @throws PolicyDefinitionException 정책의 조건 정의를 해석할 수 없는 경우
     */
    public List<InterventionPolicy> findApplicable(List<InterventionPolicy> policies, List<Target> targets) {
        List<InterventionPolicy> applicable = new ArrayList<>();
        for (InterventionPolicy policy : policies) {
            if (applies(policy, targets)) {
                applicable.add(policy);
            }
        }
        return applicable;
    }

    /**
     * 정책이 발동하는지 판별한다.
     *
     * <p>항목 중 하나라도 모든 조건을 만족하면 발동이다. 조건이 비어 있는
     * 정책은 모든 청구에 발동하게 되므로 정의 오류로 본다. 실수로 조건을
     * 지운 정책이 전 건에 복수인 확인을 요구하면 심사가 멈춘다.
     *
     * @param policy 평가할 정책
     * @param targets 항목들의 평가용 값
     * @return 발동하면 {@code true}
     * @throws PolicyDefinitionException 조건이 비었거나 해석할 수 없는 경우
     */
    public boolean applies(InterventionPolicy policy, List<Target> targets) {
        List<Map<String, Object>> conditions = parseConditions(policy);
        if (conditions.isEmpty()) {
            throw new PolicyDefinitionException(policy.getCode(), "발동 조건이 비어 있습니다");
        }
        for (Target target : targets) {
            if (satisfiesAll(policy, target, conditions)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 한 항목이 모든 조건을 만족하는지 확인한다.
     *
     * @param policy 평가 중인 정책. 오류 메시지에 코드를 담기 위해 받는다
     * @param target 평가할 항목의 값
     * @param conditions 조건 목록
     * @return 모든 조건을 만족하면 {@code true}
     */
    private boolean satisfiesAll(InterventionPolicy policy, Target target,
                                 List<Map<String, Object>> conditions) {
        for (Map<String, Object> condition : conditions) {
            if (!matches(policy, target, condition)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 조건 하나를 평가한다.
     *
     * @param policy 평가 중인 정책
     * @param target 평가할 항목의 값
     * @param condition {@code field}, {@code op}, {@code value} 를 담은 조건
     * @return 조건을 만족하면 {@code true}
     * @throws PolicyDefinitionException 필드나 연산자를 해석할 수 없는 경우
     */
    private boolean matches(InterventionPolicy policy, Target target, Map<String, Object> condition) {
        String field = text(policy, condition, "field");
        String op = text(policy, condition, "op");
        Object value = condition.get("value");
        if (value == null) {
            throw new PolicyDefinitionException(policy.getCode(), "조건 " + field + " 에 비교할 값이 없습니다");
        }

        return switch (field) {
            case FIELD_CLAIMED_AMOUNT -> compareNumber(
                    policy, field, op, toDecimal(policy, field, target.claimedAmount()), toDecimal(policy, field, value));
            case FIELD_EXCLUSION_PROBABILITY -> target.exclusionProbability() != null
                    && compareNumber(policy, field, op, target.exclusionProbability(), toDecimal(policy, field, value));
            case FIELD_COVERAGE_TYPE -> compareCoverageType(policy, op, target.coverageType(), value);
            default -> throw new PolicyDefinitionException(
                    policy.getCode(), "지원하지 않는 필드입니다: " + field);
        };
    }

    /**
     * 수치 조건을 평가한다.
     *
     * @param policy 평가 중인 정책
     * @param field 조건이 참조하는 필드명
     * @param op 연산자
     * @param actual 항목의 실제 값
     * @param expected 조건이 요구하는 값
     * @return 조건을 만족하면 {@code true}
     * @throws PolicyDefinitionException 연산자를 해석할 수 없는 경우
     */
    private boolean compareNumber(InterventionPolicy policy, String field, String op,
                                  BigDecimal actual, BigDecimal expected) {
        if (actual == null) {
            return false;
        }
        return switch (op) {
            case OP_GTE -> actual.compareTo(expected) >= 0;
            case OP_LTE -> actual.compareTo(expected) <= 0;
            case OP_EQ -> actual.compareTo(expected) == 0;
            default -> throw new PolicyDefinitionException(
                    policy.getCode(), field + " 에 지원하지 않는 연산자입니다: " + op);
        };
    }

    /**
     * 담보 분류 조건을 평가한다.
     *
     * <p>{@code eq} 만 허용한다. 담보 분류에는 순서가 없으므로 크기 비교가
     * 성립하지 않는다. 열거형에 없는 값을 비교하는 것은 오류로 보지 않고
     * 단순히 만족하지 않는 것으로 처리하는데, 담보 분류가 나중에 추가될 수
     * 있고 그때 옛 정책이 전부 500 을 내는 것보다 발동하지 않는 편이 낫다.
     *
     * @param policy 평가 중인 정책
     * @param op 연산자
     * @param actual 항목의 담보 분류
     * @param value 조건이 요구하는 값
     * @return 조건을 만족하면 {@code true}
     * @throws PolicyDefinitionException 연산자가 {@code eq} 가 아닌 경우
     */
    private boolean compareCoverageType(InterventionPolicy policy, String op,
                                        CoverageType actual, Object value) {
        if (!OP_EQ.equals(op)) {
            throw new PolicyDefinitionException(
                    policy.getCode(), FIELD_COVERAGE_TYPE + " 에는 eq 만 사용할 수 있습니다: " + op);
        }
        return actual != null && actual.name().equals(String.valueOf(value));
    }

    /**
     * 정책의 조건 JSON 을 파싱한다.
     *
     * <p>파싱에 실패하면 예외가 그대로 올라간다. Jackson 3 의 예외는
     * 비검사 예외이며, 여기서 삼켜 빈 조건으로 취급하면 통제 정책이
     * 조용히 무력화된다.
     *
     * @param policy 파싱할 정책
     * @return 조건 목록
     */
    private List<Map<String, Object>> parseConditions(InterventionPolicy policy) {
        return jsonMapper.readValue(
                policy.getConditions(), new TypeReference<List<Map<String, Object>>>() { });
    }

    /**
     * 조건에서 문자열 값을 꺼낸다.
     *
     * @param policy 평가 중인 정책
     * @param condition 조건
     * @param key 꺼낼 키
     * @return 문자열 값
     * @throws PolicyDefinitionException 값이 없는 경우
     */
    private String text(InterventionPolicy policy, Map<String, Object> condition, String key) {
        Object value = condition.get(key);
        if (value == null) {
            throw new PolicyDefinitionException(policy.getCode(), "조건에 " + key + " 가 없습니다");
        }
        return String.valueOf(value);
    }

    /**
     * 값을 {@link BigDecimal} 로 바꾼다.
     *
     * <p>금액과 확률을 모두 {@link BigDecimal} 로 다루는 이유는 부동소수점
     * 비교를 피하기 위해서다. 확률 0.8 을 {@code double} 로 비교하면 임계값
     * 경계에서 판정이 흔들리고, 그 경계가 곧 통제가 걸리는지 마는지의
     * 기준이 된다.
     *
     * @param policy 평가 중인 정책
     * @param field 조건이 참조하는 필드명
     * @param value 변환할 값
     * @return 변환된 값. 인자가 {@code null} 이면 {@code null}
     * @throws PolicyDefinitionException 수치로 해석할 수 없는 경우
     */
    private BigDecimal toDecimal(InterventionPolicy policy, String field, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new PolicyDefinitionException(
                    policy.getCode(), field + " 의 값을 수치로 해석할 수 없습니다: " + value);
        }
    }
}
