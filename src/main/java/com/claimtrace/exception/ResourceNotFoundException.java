package com.claimtrace.exception;

import java.util.Map;

/**
 * 요청한 자원이 존재하지 않음을 나타내는 예외.
 *
 * <p>불변조건 위반과 구분한 이유는 성격이 다르기 때문이다. 존재하지 않는
 * 청구를 조회하는 것은 설계가 금지한 상태를 만들려는 시도가 아니라 단순히
 * 대상이 없는 것이다. {@link InvariantViolationException} 과 섞으면 응답의
 * {@code invariant} 필드가 의미를 잃는다.
 */
public class ResourceNotFoundException extends RuntimeException {

    private final transient Map<String, Object> details;

    /**
     * 자원 종류와 식별자를 담아 예외를 만든다.
     *
     * @param resource 자원 종류. 예: {@code claim}, {@code claimItem}
     * @param id 찾지 못한 식별자
     */
    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " 를 찾을 수 없습니다. id=" + id);
        this.details = Map.of("resource", resource, "id", String.valueOf(id));
    }

    /**
     * 위반 상세를 반환한다.
     *
     * @return 자원 종류와 식별자를 담은 맵
     */
    public Map<String, Object> getDetails() {
        return details;
    }
}
