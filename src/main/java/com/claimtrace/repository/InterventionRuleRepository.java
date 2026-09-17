package com.claimtrace.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.claimtrace.domain.InterventionRule;

/**
 * 개입 규칙 조회.
 *
 * <p>D-5 의 "조건을 데이터로 둔다"가 실제로 성립하려면 확정 시점에 규칙을
 * DB 에서 읽어 평가해야 한다. 조건이 코드에 있었다면 이 리포지토리 자체가
 * 필요 없었을 것이고, 심사관리자가 화면 12 에서 조건을 바꿔도 시스템 동작은
 * 달라지지 않았을 것이다.
 */
public interface InterventionRuleRepository extends JpaRepository<InterventionRule, Long> {

    /**
     * 활성 상태인 규칙을 모두 조회한다.
     *
     * <p>비활성 규칙은 새 개입을 요구하지 않는다. 다만 이미 발생한 개입은
     * 규칙을 FK 로 들고 있으므로, 규칙을 꺼도 과거 기록의 근거는 남는다.
     *
     * @return 코드 오름차순의 활성 규칙 목록
     */
    @Query("SELECT p FROM InterventionRule p WHERE p.active = true ORDER BY p.code ASC")
    List<InterventionRule> findAllActive();
}
