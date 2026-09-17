package com.claimtrace.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.claimtrace.domain.Intervention;
import com.claimtrace.domain.enums.InterventionType;

/**
 * 개입 이력 조회.
 *
 * <p>확정 서비스가 INV-4 를 검사할 때 이 리포지토리로 청구의 개입을 모두
 * 읽고 {@code Intervention.blocksDecision()} 으로 판별한다. 승인 여부를
 * 질의 조건에 넣지 않는 이유는, "확정을 막는다"의 정의가 엔티티에 있어야
 * 승인 대기와 반려를 함께 다루는 규칙이 한 곳에 남기 때문이다.
 */
public interface InterventionRepository extends JpaRepository<Intervention, Long> {

    /**
     * 청구의 모든 개입 이력을 조회한다.
     *
     * @param claimId 청구 식별자
     * @return 발생 시각 오름차순의 개입 목록
     */
    @Query("""
            SELECT n FROM Intervention n
            LEFT JOIN FETCH n.interventionRule
            WHERE n.claim.id = :claimId
            ORDER BY n.occurredAt ASC, n.id ASC
            """)
    List<Intervention> findAllByClaimId(@Param("claimId") Long claimId);

    /**
     * 특정 규칙에 의한 개입이 이 청구에 이미 기록되어 있는지 확인한다.
     *
     * <p>판정을 저장할 때마다 규칙을 평가하므로, 같은 청구의 여러 항목을
     * 차례로 판정하면 같은 규칙이 반복해서 발동한다. 승인 대기 레코드가
     * 항목 수만큼 쌓이면 심사관리자가 같은 건을 여러 번 승인해야 하고,
     * INV-4 검사에서도 하나만 승인되고 나머지가 대기로 남아 확정이 막힌다.
     *
     * @param claimId 청구 식별자
     * @param ruleId 개입 규칙 식별자
     * @return 이미 기록되어 있으면 {@code true}
     */
    @Query("""
            SELECT COUNT(n) > 0 FROM Intervention n
            WHERE n.claim.id = :claimId AND n.interventionRule.id = :ruleId
            """)
    boolean existsByClaimIdAndRuleId(@Param("claimId") Long claimId,
                                       @Param("ruleId") Long ruleId);

    /**
     * 청구에서 특정 유형의 개입이 몇 건 발생했는지 센다.
     *
     * <p>확정 응답의 {@code overrideCount} 에 쓴다. 이 청구에서 AI 권고가
     * 몇 번 뒤집혔는지를 보여주는 값이며, 보조수단성 ⑧이 요구하는
     * "인적 개입 이행 결과의 기록"이 집계 가능하다는 것을 드러낸다.
     *
     * @param claimId 청구 식별자
     * @param type 개입 유형
     * @return 해당 유형의 개입 건수
     */
    @Query("SELECT COUNT(n) FROM Intervention n WHERE n.claim.id = :claimId AND n.type = :type")
    long countByClaimIdAndType(@Param("claimId") Long claimId, @Param("type") InterventionType type);
}
