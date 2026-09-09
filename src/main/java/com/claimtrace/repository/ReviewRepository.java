package com.claimtrace.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.claimtrace.domain.Review;

/**
 * 판정 조회.
 *
 * <p>{@code isCurrent} 를 다루는 질의가 모여 있다. 이 리포지토리의 메서드가
 * INV-12(항목당 현재 판정 정확히 1건)의 성립 여부를 관찰하는 창구다.
 *
 * <p>메서드 이름 파생 대신 JPQL 을 명시한 이유는 {@code isCurrent} 처럼
 * {@code is} 로 시작하는 필드명이 파생 규칙에서 모호해질 수 있어서다.
 * 질의를 눈으로 읽고 확인할 수 있는 편이 낫다.
 */
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * 항목의 현재 유효한 판정을 조회한다.
     *
     * <p>판정 저장 시 이전 판정을 찾아 플래그를 내리는 데 쓰고, 확정 시
     * 미판정 항목을 가려내는 데도 쓴다. 결과가 없으면 아직 판정되지 않은
     * 항목이다.
     *
     * @param itemId 항목 식별자
     * @return 현재 판정. 미판정이면 빈 {@link Optional}
     */
    @Query("SELECT r FROM Review r WHERE r.claimItem.id = :itemId AND r.isCurrent = true")
    Optional<Review> findCurrentByItemId(@Param("itemId") Long itemId);

    /**
     * 항목의 전체 판정 이력을 최신순으로 조회한다.
     *
     * <p>화면 9 의 판정 이력에 쓴다. 대체된 판정도 모두 포함한다(D-6).
     *
     * @param itemId 항목 식별자
     * @return 판정 시각 내림차순의 판정 목록
     */
    @Query("""
            SELECT r FROM Review r
            JOIN FETCH r.reviewer
            WHERE r.claimItem.id = :itemId
            ORDER BY r.decidedAt DESC
            """)
    List<Review> findHistoryByItemId(@Param("itemId") Long itemId);

    /**
     * 청구에 속한 모든 항목의 현재 판정을 조회한다.
     *
     * <p>확정 서비스가 INV-10 을 검사할 때 쓴다. 이 결과의 개수가 청구의
     * 항목 개수보다 적으면 미판정 항목이 남아 있다는 뜻이다.
     *
     * @param claimId 청구 식별자
     * @return 현재 판정 목록
     */
    @Query("""
            SELECT r FROM Review r
            WHERE r.claimItem.claim.id = :claimId AND r.isCurrent = true
            """)
    List<Review> findCurrentByClaimId(@Param("claimId") Long claimId);

    /**
     * 항목의 현재 판정 개수를 센다.
     *
     * <p>INV-12 검증 테스트 전용이다. H2 가 부분 유니크 인덱스를 지원하지
     * 않아 DB 가 이 조건을 막지 못하므로, 서비스가 실제로 지켰는지를 이
     * 개수로 확인한다. 언제나 0 또는 1 이어야 한다.
     *
     * @param itemId 항목 식별자
     * @return 현재 판정으로 표시된 레코드 수
     */
    @Query("SELECT COUNT(r) FROM Review r WHERE r.claimItem.id = :itemId AND r.isCurrent = true")
    long countCurrentByItemId(@Param("itemId") Long itemId);
}
