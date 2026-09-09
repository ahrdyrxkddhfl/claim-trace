package com.claimtrace.exception;

import java.util.Map;

/**
 * 설계 불변조건을 위반하는 요청이 들어왔음을 나타내는 예외.
 *
 * <p>서비스 계층이 검증에 실패했을 때 던지는 단일 예외 타입이다. 불변조건마다
 * 예외 클래스를 따로 만들지 않은 이유는, 처리 방식이 전부 같기 때문이다.
 * 어느 규칙을 위반했는지는 {@link ErrorCode} 가 들고 있고, 전역 처리기가
 * 그 코드에서 HTTP 상태와 응답 본문을 만든다. 클래스를 열 개 만들면 예외
 * 처리기도 열 개가 되고, 새 불변조건이 늘 때마다 두 곳을 고쳐야 한다.
 *
 * <p>이 예외가 던져지면 트랜잭션은 롤백된다. {@link RuntimeException} 을
 * 상속하므로 Spring 의 기본 롤백 규칙에 걸린다. 판정 저장처럼 한 트랜잭션에서
 * 여러 레코드를 건드리는 경우, 중간에 검증이 실패하면 앞서 만든 레코드도
 * 함께 사라져야 한다. 개입 기록 없이 판정만 남는 상태가 만들어지면 INV-3 이
 * 무너진다.
 */
public class InvariantViolationException extends RuntimeException {

    private final transient ErrorCode errorCode;
    private final transient Map<String, Object> details;

    /**
     * 상세 정보와 함께 예외를 만든다.
     *
     * @param errorCode 위반에 대응하는 오류 코드
     * @param details 위반 상세. 응답의 {@code details} 에 그대로 실린다
     */
    public InvariantViolationException(ErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = details;
    }

    /**
     * 상세 정보 없이 예외를 만든다.
     *
     * @param errorCode 위반에 대응하는 오류 코드
     */
    public InvariantViolationException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    /**
     * 오류 코드를 반환한다.
     *
     * @return 오류 코드
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * 위반 상세를 반환한다.
     *
     * @return 위반 상세. 없으면 {@code null}
     */
    public Map<String, Object> getDetails() {
        return details;
    }
}
