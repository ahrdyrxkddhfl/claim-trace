package com.claimtrace.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.claimtrace.domain.Explanation;

/**
 * 설명서 조회.
 */
public interface ExplanationRepository extends JpaRepository<Explanation, Long> {

    /**
     * 청구의 국소 설명서를 조회한다.
     *
     * <p>초안 생성이 반복 호출될 수 있으므로, 이미 만들어진 설명서가 있으면
     * 새로 만들지 않고 본문만 갱신한다. 호출할 때마다 설명서 레코드가 쌓이면
     * 어느 것이 유효한지 알 수 없게 된다.
     *
     * @param claimId 청구 식별자
     * @return 국소 설명서. 없으면 빈 {@link Optional}
     */
    @Query("SELECT e FROM Explanation e WHERE e.claim.id = :claimId AND e.type = 'LOCAL'")
    Optional<Explanation> findLocalByClaimId(@Param("claimId") Long claimId);
}
