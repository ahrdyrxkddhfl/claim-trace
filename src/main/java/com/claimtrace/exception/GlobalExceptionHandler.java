package com.claimtrace.exception;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;

/**
 * 전역 예외 처리기. 엔드포인트에 닿은 요청의 오류 응답은 모두 이 한 곳을 지난다.
 *
 * <p>컨트롤러가 예외를 잡아 응답을 만들지 않는 이유는, 같은 불변조건 위반이
 * 엔드포인트마다 다른 형태로 나가는 것을 막기 위해서다. 판정 저장에서의
 * INV-7 위반과 근거 수정에서의 INV-7 위반은 같은 응답이어야 한다.
 *
 * <p>처리하는 예외는 다음과 같다.
 * <ul>
 *   <li>{@link InvariantViolationException} — 서비스가 던지는 설계 위반</li>
 *   <li>{@link ResourceNotFoundException} — 대상 없음</li>
 *   <li>{@link RuleDefinitionException} — 개입 규칙 데이터 오류</li>
 *   <li>{@link MethodArgumentNotValidException} — 요청 본문의 형식 검증 실패</li>
 *   <li>{@link MissingRequestHeaderException},
 *       {@link HttpMessageNotReadableException} — 컨트롤러에 들어오기 전에
 *       Spring 이 거부한 요청(헤더 누락, 정의되지 않은 enum 값, 깨진 JSON)</li>
 *   <li>{@link IllegalArgumentException}, {@link IllegalStateException} —
 *       엔티티가 스스로 막은 경우</li>
 * </ul>
 *
 * <p>마지막 항목이 안전망이다. 엔티티 생성자와 상태 전이 메서드가 자체
 * 검증을 갖고 있는데(예: 사유가 공백인 판정 생성), 서비스가 그 검증을
 * 빠뜨려도 엔티티가 막는다. 다만 그 경우 불변조건 번호를 알 수 없어
 * {@code invariant} 가 비게 된다. 정상 경로는 서비스가 먼저 검사해
 * {@link InvariantViolationException} 을 던지는 것이며, 이 처리기가
 * 동작한다면 서비스에 검증이 빠졌다는 신호다.
 *
 * <p>경로·메서드·미디어 타입이 맞지 않는 요청(404 · 405 · 415)은 처리하지
 * 않아 Spring 기본 형식으로 나간다. 어느 엔드포인트에도 닿지 않은 요청이라
 * 불변조건과 무관하다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 의존성이 없는 처리기다. Spring 이 기본 생성자로 만든다. */
    public GlobalExceptionHandler() {
        // 상태를 갖지 않는다.
    }

    /**
     * 설계 불변조건 위반을 처리한다.
     *
     * @param ex 발생한 예외
     * @return 오류 코드가 정한 상태와 본문
     */
    @ExceptionHandler(InvariantViolationException.class)
    public ResponseEntity<ErrorResponse> handleInvariantViolation(InvariantViolationException ex) {
        ErrorCode code = ex.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, ex.getDetails()));
    }

    /**
     * 대상 자원 없음을 처리한다.
     *
     * @param ex 발생한 예외
     * @return 404 와 자원 종류·식별자를 담은 본문
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getStatus())
                .body(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND, ex.getDetails()));
    }

    /**
     * 개입 규칙 정의 오류를 처리한다.
     *
     * <p>운영 데이터의 문제이므로 500 이다. 어느 규칙이 문제인지 응답에
     * 담아 심사관리자가 화면 12 에서 바로 찾아갈 수 있게 한다.
     *
     * @param ex 발생한 예외
     * @return 500 과 규칙 코드·문제 설명을 담은 본문
     */
    @ExceptionHandler(RuleDefinitionException.class)
    public ResponseEntity<ErrorResponse> handleRuleDefinition(RuleDefinitionException ex) {
        return ResponseEntity.status(ErrorCode.RULE_DEFINITION_INVALID.getStatus())
                .body(ErrorResponse.of(ErrorCode.RULE_DEFINITION_INVALID, ex.getDetails()));
    }

    /**
     * 요청 본문의 형식 검증 실패를 처리한다.
     *
     * <p>필드별 위반 사유를 {@code details} 에 모아 담는다. 어느 필드가
     * 왜 거부되었는지를 클라이언트가 화면에 표시할 수 있어야 하기 때문이다.
     *
     * @param ex 발생한 예외
     * @return 400 과 필드별 위반 사유를 담은 본문
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, fields));
    }

    /**
     * 필수 요청 헤더가 없는 경우를 처리한다.
     *
     * <p>행위자 헤더 {@code X-Actor-Id} 가 대표적이다. 처리하지 않으면 Spring
     * 기본 형식의 400 이 나가, 클라이언트가 {@code code} 로 분기할 수 없다.
     *
     * @param ex 발생한 예외
     * @return 400 과 빠진 헤더 이름
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, Map.of("header", ex.getHeaderName())));
    }

    /**
     * 요청 본문을 읽을 수 없는 경우를 처리한다.
     *
     * <p>정의되지 않은 enum 값이면 Jackson 의 {@link InvalidFormatException} 이
     * 원인으로 실려 온다. 그때는 어느 필드에 어떤 값이 들어왔고 무엇이
     * 허용되는지를 {@code details} 에 담는다. 판정 값을 잘못 쓴 클라이언트가
     * 허용값을 응답에서 바로 알 수 있게 하기 위해서다. 그 밖의 경우(깨진 JSON,
     * 빈 본문)는 본문을 읽지 못했다는 사실만 알린다. 파서의 원문 메시지에는
     * 내부 클래스 이름이 들어 있어 그대로 싣지 않는다.
     *
     * @param ex 발생한 예외
     * @return 400 과 문제가 된 필드·값, 또는 본문을 읽지 못했다는 설명
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (ex.getMostSpecificCause() instanceof InvalidFormatException invalid) {
            List<JacksonException.Reference> path = invalid.getPath();
            if (!path.isEmpty()) {
                details.put("field", path.get(path.size() - 1).getPropertyName());
            }
            details.put("value", String.valueOf(invalid.getValue()));
            Class<?> target = invalid.getTargetType();
            if (target != null && target.isEnum()) {
                details.put("allowed", Arrays.stream(target.getEnumConstants())
                        .map(constant -> ((Enum<?>) constant).name())
                        .toList());
            }
        } else {
            details.put("detail", "요청 본문을 JSON 으로 읽을 수 없습니다");
        }
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, details));
    }

    /**
     * 엔티티가 스스로 막은 잘못된 인자를 처리한다.
     *
     * <p>서비스가 먼저 검사했어야 하는 경우다. 여기까지 왔다는 것은 검증이
     * 한 겹 빠졌다는 뜻이므로, 응답은 400 으로 나가되 메시지에 엔티티가 남긴
     * 문구를 그대로 실어 어디가 막았는지 드러나게 한다.
     *
     * @param ex 발생한 예외
     * @return 400 과 엔티티가 남긴 메시지
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, Map.of("detail", ex.getMessage())));
    }

    /**
     * 엔티티가 스스로 막은 잘못된 상태 전이를 처리한다.
     *
     * <p>이미 확정된 청구를 다시 확정하려는 경우가 대표적이다. 상태 충돌이므로
     * 409 로 응답한다.
     *
     * <p>특정 불변조건 코드로 단정하지 않고 일반적인 상태 전이 오류로
     * 응답한다. 이 처리기는 어떤 엔티티가 왜 막았는지 알지 못하므로,
     * 예컨대 모든 {@link IllegalStateException} 을 "확정된 청구는 변경할 수
     * 없습니다"로 바꾸면 청구와 무관한 예외까지 그 문구와 그 불변조건
     * 번호를 달고 나간다. 응답의 {@code invariant} 필드는 그렇게 붙이면
     * 신뢰할 수 없는 값이 된다.
     *
     * @param ex 발생한 예외
     * @return 409 와 엔티티가 남긴 메시지
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(ErrorCode.INVALID_STATE_TRANSITION.getStatus())
                .body(ErrorResponse.of(
                        ErrorCode.INVALID_STATE_TRANSITION, Map.of("detail", ex.getMessage())));
    }
}
