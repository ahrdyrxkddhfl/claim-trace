package com.claimtrace.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

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
     * 판정을 저장하기 위해 항목을 배타 잠금으로 조회한다.
     *
     * <p><b>INV-12 를 동시 요청에서 지키기 위한 장치다.</b> 판정 저장은
     * "이전 현재 판정을 읽고 → 새 판정을 저장하고 → 이전 판정의 플래그를
     * 내린다"는 순서로 동작한다. 같은 항목에 두 요청이 동시에 들어오면 둘 다
     * 첫 단계에서 "이전 판정 없음"을 읽고, 둘 다 현재 판정으로 저장된다.
     * 플래그를 내릴 대상을 서로 찾지 못하기 때문이다.
     *
     * <p>이 조회가 {@code SELECT ... FOR UPDATE} 를 발행해 항목 행을 잠근다.
     * 먼저 도착한 트랜잭션이 커밋할 때까지 뒤따르는 트랜잭션은 이 지점에서
     * 대기하므로, 읽기와 쓰기 사이에 다른 요청이 끼어들 수 없다. 뒤따르는
     * 요청은 대기 후 갱신된 상태를 읽어 정상적으로 이전 판정을 물린다.
     * 요청이 거부되지 않고 직렬화될 뿐이라는 점이 중요하다.
     *
     * <p>잠금 대상을 판정이 아니라 <b>항목</b>으로 잡은 이유는, 막아야 하는
     * 것이 "같은 항목에 대한 동시 판정"이고 첫 판정 시점에는 잠글 판정
     * 레코드가 아직 없기 때문이다. 항목은 언제나 존재한다.
     *
     * <p>연관을 페치 조인하지 않는다. {@code FOR UPDATE} 와 조인을 함께 쓰면
     * DB 에 따라 조인된 테이블까지 잠기거나 문법 오류가 난다. 청구는 같은
     * 트랜잭션 안에서 지연 로딩으로 읽으면 충분하다.
     *
     * @param itemId 항목 식별자
     * @return 잠금이 걸린 항목. 없으면 빈 {@link Optional}
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM ClaimItem i WHERE i.id = :itemId")
    Optional<ClaimItem> findByIdForUpdate(@Param("itemId") Long itemId);

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
