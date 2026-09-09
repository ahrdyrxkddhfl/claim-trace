package com.claimtrace.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.claimtrace.domain.Claim;

/**
 * 청구 조회.
 *
 * <p>{@link #findWithCustomerAndPolicy(Long)} 는 연관 엔티티를 페치 조인으로
 * 함께 읽는다. 청구 상세 화면은 언제나 청구인명과 증권번호를 함께 표시하는데,
 * 지연 로딩만 두면 응답을 만드는 시점에 추가 쿼리가 두 번 더 나간다.
 */
public interface ClaimRepository extends JpaRepository<Claim, Long> {

    /**
     * 청구를 청구인·계약과 함께 조회한다.
     *
     * @param claimId 청구 식별자
     * @return 청구. 없으면 빈 {@link Optional}
     */
    @Query("""
            SELECT c FROM Claim c
            JOIN FETCH c.customer
            JOIN FETCH c.policy
            WHERE c.id = :claimId
            """)
    Optional<Claim> findWithCustomerAndPolicy(@Param("claimId") Long claimId);
}
