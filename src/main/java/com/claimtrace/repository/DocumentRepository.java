package com.claimtrace.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.claimtrace.domain.Document;

/**
 * 제출 서류 조회.
 *
 * <p>근거가 서류를 지목할 때 그 서류가 같은 청구의 것인지 확인하는 데 쓴다.
 * 다른 청구의 서류를 근거로 연결할 수 있으면, 판정의 출처를 추적했을 때
 * 존재하지 않는 경로가 나온다.
 */
public interface DocumentRepository extends JpaRepository<Document, Long> {

    /**
     * 특정 청구에 속한 서류를 조회한다.
     *
     * <p>식별자만으로 조회한 뒤 소속을 검사하지 않고 질의 조건에 함께 넣는
     * 이유는, 검사를 잊는 경로를 없애기 위해서다. 다른 청구의 서류는
     * 조회 결과가 비어 있어 그대로 거부된다.
     *
     * @param documentId 서류 식별자
     * @param claimId 청구 식별자
     * @return 해당 청구의 서류. 없거나 다른 청구의 것이면 빈 {@link Optional}
     */
    @Query("SELECT d FROM Document d WHERE d.id = :documentId AND d.claim.id = :claimId")
    Optional<Document> findByIdAndClaimId(@Param("documentId") Long documentId,
                                          @Param("claimId") Long claimId);
}
