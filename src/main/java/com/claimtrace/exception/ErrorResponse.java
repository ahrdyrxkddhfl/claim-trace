package com.claimtrace.exception;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 공통 오류 응답 본문. OpenAPI 명세의 {@code Error} 스키마에 대응한다.
 *
 * <p>{@code invariant} 와 {@code details} 두 항목이 이 스키마의 특징이다.
 * 전자는 어느 설계 규칙이 이 응답을 만들었는지 밝히고, 후자는 무엇 때문에
 * 걸렸는지를 구조화해 담는다. 예를 들어 오버라이드 사유 누락이면
 * {@code {"recommendation":"DENY","decision":"PARTIAL"}} 이 실려, 클라이언트가
 * 어떤 값 때문에 사유가 요구되었는지 화면에 그대로 보여줄 수 있다.
 *
 * <p>{@code details} 의 구조는 오류 유형마다 다르다. 고정하면 담을 수 없는
 * 정보가 생기고, 유형별로 스키마를 나누면 클라이언트가 응답 처리 분기를
 * 오류 개수만큼 갖게 된다.
 *
 * @param code 오류 코드. {@link ErrorCode} 의 이름과 같다
 * @param message 사용자에게 보일 한국어 메시지
 * @param invariant 위반된 불변조건 번호. 불변조건과 무관하면 {@code null}
 * @param details 위반 상세. 없으면 {@code null}
 * @param timestamp 응답 생성 시각
 */
public record ErrorResponse(
        String code,
        String message,
        String invariant,
        Map<String, Object> details,
        OffsetDateTime timestamp) {

    /**
     * 오류 코드로부터 응답 본문을 만든다.
     *
     * <p>메시지와 불변조건 번호는 코드가 들고 있으므로 호출부가 다시 적지
     * 않는다. 같은 오류에 서로 다른 문구가 나가는 것을 막는다.
     *
     * @param code 오류 코드
     * @param details 위반 상세. 없으면 {@code null}
     * @return 오류 응답 본문
     */
    public static ErrorResponse of(ErrorCode code, Map<String, Object> details) {
        return new ErrorResponse(
                code.name(), code.getMessage(), code.getInvariant(), details, OffsetDateTime.now());
    }
}
