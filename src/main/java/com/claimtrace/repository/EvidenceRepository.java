package com.claimtrace.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.claimtrace.domain.Evidence;

/**
 * 근거 조회.
 *
 * <p>고객용 필터링을 이 계층에서 하지 않는다는 점이 중요하다. 근거는 자기가
 * 공개 가능한지를 {@code disclosureLevel} 로 들고 있고(D-4), 설명문 생성
 * 서비스가 {@code Evidence.isUsableInCustomerExplanation()} 으로 판단한다.
 * 질의로 걸러내면 판단 기준이 SQL 과 자바 양쪽에 흩어져 언젠가 어긋난다.
 */
public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    /**
     * 근거를 소속 항목·청구와 함께 조회한다.
     *
     * <p>근거 상태 변경 서비스의 진입점이다. INV-7 검사를 위해 청구의 상태를
     * 알아야 하므로 두 단계를 페치 조인으로 함께 읽는다.
     *
     * @param evidenceId 근거 식별자
     * @return 근거. 없으면 빈 {@link Optional}
     */
    @Query("""
            SELECT e FROM Evidence e
            JOIN FETCH e.claimItem i
            JOIN FETCH i.claim
            WHERE e.id = :evidenceId
            """)
    Optional<Evidence> findWithClaim(@Param("evidenceId") Long evidenceId);

    /**
     * 항목의 모든 근거를 조회한다.
     *
     * <p>화면 10 근거 카드 목록에 쓴다. 기각된 근거도 포함한다(D-3).
     * 룰 근거의 조항 원문을 함께 표시하므로 룰을 페치 조인하되, AI 근거와
     * 수동 근거는 룰이 없으므로 왼쪽 조인이어야 한다.
     *
     * @param itemId 항목 식별자
     * @return 생성 시각 오름차순의 근거 목록
     */
    @Query("""
            SELECT e FROM Evidence e
            LEFT JOIN FETCH e.rule
            LEFT JOIN FETCH e.document
            WHERE e.claimItem.id = :itemId
            ORDER BY e.createdAt ASC, e.id ASC
            """)
    List<Evidence> findAllByItemId(@Param("itemId") Long itemId);

    /**
     * 청구에 속한 모든 근거를 조회한다.
     *
     * <p>설명문 초안 생성 서비스가 쓴다. 항목별로 나누어 조회하면 항목 수만큼
     * 쿼리가 나가므로 한 번에 읽고 애플리케이션에서 항목별로 묶는다.
     *
     * @param claimId 청구 식별자
     * @return 근거 목록
     */
    @Query("""
            SELECT e FROM Evidence e
            JOIN FETCH e.claimItem
            WHERE e.claimItem.claim.id = :claimId
            ORDER BY e.claimItem.seq ASC, e.id ASC
            """)
    List<Evidence> findAllByClaimId(@Param("claimId") Long claimId);
}
