package com.claimtrace.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.claimtrace.domain.User;

/**
 * 사내 사용자 조회.
 *
 * <p>인증이 구현 범위 밖이므로 로그인 검증에는 쓰이지 않는다. 요청 헤더로
 * 전달된 행위자 식별자를 실제 사용자로 바꾸는 데에만 쓴다. INV-1 이
 * 요구하는 행위자가 실재하는 사람인지 확인하는 것이 이 리포지토리의 역할이다.
 */
public interface UserRepository extends JpaRepository<User, Long> {
}
