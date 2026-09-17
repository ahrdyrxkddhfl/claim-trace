package com.claimtrace.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.claimtrace.domain.Claim;

/**
 * 청구 조회.
 *
 * <p>사용자 정의 질의가 없다. 청구를 다루는 서비스는 식별자로 단건을 읽은 뒤
 * 항목·판정·개입을 각각의 리포지토리에서 가져온다. 청구인과 계약을 함께
 * 읽는 페치 조인을 두었다가 제거했는데, 확정과 설명문 생성 어느 쪽도 그
 * 값을 쓰지 않아 호출되지 않는 질의로 남았기 때문이다. 필요해지는 시점에
 * 다시 추가한다.
 */
public interface ClaimRepository extends JpaRepository<Claim, Long> {
}
