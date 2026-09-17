package com.claimtrace.exception;

import java.util.Map;

/**
 * 개입 규칙의 발동 조건을 해석할 수 없음을 나타내는 예외.
 *
 * <p>{@link InvariantViolationException} 과 구분한 이유는 책임 소재가 다르기
 * 때문이다. 불변조건 위반은 요청이 설계가 금지한 상태를 만들려 한 것이지만,
 * 이쪽은 심사관리자가 저장해 둔 규칙 데이터가 잘못된 것이다. 요청자가
 * 고칠 수 있는 문제가 아니므로 4xx 가 아니라 500 으로 응답한다.
 *
 * <p>해석할 수 없는 규칙을 무시하고 넘어가지 않는다. 그 규칙은 규제가
 * 요구하는 통제 수단이고, 통제가 적용되지 않은 채 확정이 성공하는 상황이
 * 오류 응답보다 훨씬 나쁘다. 조용한 실패를 만들지 않는 것이 이 예외의
 * 존재 이유다.
 */
public class RuleDefinitionException extends RuntimeException {

    private final transient Map<String, Object> details;

    /**
     * 어느 규칙의 무엇이 잘못되었는지 담아 예외를 만든다.
     *
     * @param ruleCode 문제가 있는 규칙의 코드
     * @param problem 무엇이 잘못되었는지에 대한 설명
     */
    public RuleDefinitionException(String ruleCode, String problem) {
        super("개입 규칙 " + ruleCode + " 의 조건이 잘못되었습니다. " + problem);
        this.details = Map.of("ruleCode", ruleCode, "problem", problem);
    }

    /**
     * 위반 상세를 반환한다.
     *
     * @return 규칙 코드와 문제 설명을 담은 맵
     */
    public Map<String, Object> getDetails() {
        return details;
    }
}
