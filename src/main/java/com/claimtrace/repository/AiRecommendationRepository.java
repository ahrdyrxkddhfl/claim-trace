package com.claimtrace.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.claimtrace.domain.AiRecommendation;

/**
 * AI 권고 조회.
 *
 * <p>판정 저장 서비스가 개입 여부를 판별하려면 "지금 유효한 권고"를 정확히
 * 하나 집어야 한다. 모델 버전이 바뀌면 한 항목에 권고가 여러 건 쌓이므로
 * {@code isLatest} 로 걸러야 하며, 이것이 D-1 이 별도 테이블을 택한 이유이기도
 * 하다.
 */
public interface AiRecommendationRepository extends JpaRepository<AiRecommendation, Long> {

    /**
     * 항목의 최신 AI 권고를 조회한다.
     *
     * <p>판정 저장 시 심사자의 판정과 비교되는 대상이다(D-7). 권고가 없는
     * 항목도 있을 수 있다. 모델이 산출하지 못한 항목이거나 외부 연계가
     * 아직 도착하지 않은 경우이며, 이때는 비교할 대상이 없으므로 개입
     * 기록도 생성되지 않는다.
     *
     * @param itemId 항목 식별자
     * @return 최신 권고. 없으면 빈 {@link Optional}
     */
    @Query("SELECT a FROM AiRecommendation a WHERE a.claimItem.id = :itemId AND a.isLatest = true")
    Optional<AiRecommendation> findLatestByItemId(@Param("itemId") Long itemId);

    /**
     * 청구에 속한 모든 항목의 최신 AI 권고를 조회한다.
     *
     * <p>화면 9 항목 목록과 개입 규칙 평가에 쓴다. 규칙의 발동 조건 중
     * {@code exclusionProbability} 가 이 값을 참조한다.
     *
     * @param claimId 청구 식별자
     * @return 최신 권고 목록
     */
    @Query("""
            SELECT a FROM AiRecommendation a
            WHERE a.claimItem.claim.id = :claimId AND a.isLatest = true
            """)
    List<AiRecommendation> findLatestByClaimId(@Param("claimId") Long claimId);
}
