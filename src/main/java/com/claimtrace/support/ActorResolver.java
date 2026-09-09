package com.claimtrace.support;

import org.springframework.stereotype.Component;

import com.claimtrace.domain.User;
import com.claimtrace.exception.ResourceNotFoundException;
import com.claimtrace.repository.UserRepository;

/**
 * 요청을 일으킨 행위자를 결정한다.
 *
 * <p>INV-1 은 모든 상태 전이에 행위자가 있을 것을 요구한다. 정상적인
 * 시스템이라면 인증된 세션에서 행위자를 꺼내지만, 본 구현은 인증을 범위에서
 * 제외했으므로 {@code X-Actor-Id} 요청 헤더로 받는다.
 *
 * <p>헤더를 컨트롤러마다 직접 읽지 않고 이 컴포넌트 한 곳을 거치게 한 것이
 * 요점이다. 실제 인증이 들어갈 자리가 코드에 한 군데로 표시되고, 나중에
 * Spring Security 를 붙일 때 이 클래스의 내부만 바뀐다. 헤더를 컨트롤러
 * 여기저기서 읽었다면 인증 도입이 전 파일 수정이 된다.
 *
 * <p>OpenAPI 명세에는 이 헤더가 없다. 명세가 정의한 것은 인증된 세션에서
 * 행위자가 주입되는 설계이고, 이 헤더는 그 자리를 대신하는 구현상의
 * 임시 수단이다. README 의 제외 범위에 함께 적는다.
 */
@Component
public class ActorResolver {

    private final UserRepository userRepository;

    /**
     * 의존성을 주입받는다.
     *
     * @param userRepository 사내 사용자 조회
     */
    public ActorResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 행위자 식별자를 실제 사용자로 바꾼다.
     *
     * <p>존재하지 않는 식별자면 예외를 던진다. INV-1 이 요구하는 것은 행위자
     * 컬럼이 채워지는 것이 아니라 실재하는 사람이 지목되는 것이므로, 임의의
     * 숫자가 판정의 행위자로 기록되게 두어서는 안 된다.
     *
     * @param actorId {@code X-Actor-Id} 헤더로 전달된 사용자 식별자
     * @return 해당 사용자
     * @throws ResourceNotFoundException 식별자가 {@code null} 이거나 해당 사용자가 없는 경우
     */
    public User resolve(Long actorId) {
        if (actorId == null) {
            throw new ResourceNotFoundException("user", null);
        }
        return userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("user", actorId));
    }
}
