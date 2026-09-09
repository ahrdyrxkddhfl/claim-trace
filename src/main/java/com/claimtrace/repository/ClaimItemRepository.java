package com.claimtrace.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.claimtrace.domain.ClaimItem;

/**
 * 청구 항목 조회.
 *
 * <p>판정 저장 서비스가 항목을 읽을 때는 청구까지 필요하다. INV-7 검사가
 * 청구의 상태를 봐야 하고, 개입 기록 생성 시 청구 FK 도 채워야 하기 때문이다.
 * 그래서 단건 조회에도 페치 조인을 쓴다.
 */
public interface ClaimItemRepository extends JpaRepository<ClaimItem, Long> {

    /**
     * 항목을 소속 청구와 함께 조회한다.
     *
     * <p>판정 저장 서비스의 진입점이다. 청구를 함께 읽어야 INV-7(확정된 청구는
     * 변경 불가)과 INV-6(배정 심사자 확인)을 추가 쿼리 없이 검사할 수 있다.
     *
     * @param itemId 항목 식별자
     * @return 항목. 없으면 빈 {@link Optional}
     */
    @Query("""
            SELECT i FROM ClaimItem i
            JOIN FETCH i.claim
            WHERE i.id = :itemId
            """)
    Optional<ClaimItem> findWithClaim(@Param("itemId") Long itemId);

    /**
     * 청구의 모든 항목을 담보와 함께 순번 순으로 조회한다.
     *
     * <p>담보를 페치 조인하는 이유는 화면 9 항목 목록이 담보명을 함께
     * 표시하기 때문이다. 항목이 4건이면 지연 로딩 시 쿼리가 5번 나간다.
     *
     * @param claimId 청구 식별자
     * @return 순번 오름차순의 항목 목록
     */
    @Query("""
            SELECT i FROM ClaimItem i
            JOIN FETCH i.coverage
            WHERE i.claim.id = :claimId
            ORDER BY i.seq ASC
            """)
    List<ClaimItem> findAllByClaimIdWithCoverage(@Param("claimId") Long claimId);
}
