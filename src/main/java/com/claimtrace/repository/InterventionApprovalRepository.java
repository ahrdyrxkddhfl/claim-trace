package com.claimtrace.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.claimtrace.domain.InterventionApproval;

/**
 * 개입 처리 이력 조회.
 *
 * <p>쓰기는 {@code save} 만 쓴다. 수정·삭제 메서드를 노출하지 않는 것이
 * 이 테이블의 성격이다(추가 전용).
 */
public interface InterventionApprovalRepository extends JpaRepository<InterventionApproval, Long> {

    /**
     * 여러 개입의 처리 이력을 한 번에 조회한다.
     *
     * <p>개입 수만큼 조회를 반복하지 않기 위해 한 번에 읽는다. 정렬은
     * 식별자 오름차순이다. 같은 트랜잭션에서 여러 개입을 한꺼번에 처리하면
     * 처리 시각이 동일하게 찍히므로, 시각만으로는 순서가 확정되지 않는다.
     *
     * @param interventionIds 개입 식별자 목록
     * @return 처리 순서(식별자 오름차순)의 이력 목록
     */
    @Query("""
            SELECT a FROM InterventionApproval a
            JOIN FETCH a.approver
            WHERE a.intervention.id IN :interventionIds
            ORDER BY a.id ASC
            """)
    List<InterventionApproval> findAllByInterventionIds(
            @Param("interventionIds") List<Long> interventionIds);
}
