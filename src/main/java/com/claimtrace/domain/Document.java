package com.claimtrace.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.claimtrace.domain.enums.DocumentType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 제출 서류와 OCR 결과.
 *
 * <p>청구인이 화면 3 에서 올린 파일이다. 영수증과 세부내역서는 OCR 을 거쳐
 * 청구 항목({@link ClaimItem})의 생성 근거가 되고(D-8), 근거({@code evidences})가
 * 특정 서류를 지목할 때도 이 엔티티를 참조한다.
 *
 * <p>파일 업로드와 OCR 연계는 구현 범위 밖이다. 시드 데이터로 서류가 이미
 * 올라가 있고 OCR 이 끝난 상태를 만들어 둔다. {@code fileUrl} 은 실제로
 * 접근 가능한 주소가 아니라 자리 표시자다.
 *
 * <p>{@code ocrStatus} 를 Enum 이 아니라 문자열로 둔 것은 DBML 원본이
 * {@code varchar(20)} 이기 때문이다. OCR 연계가 구현 범위 밖이라 이 값으로
 * 분기하는 로직이 없어서, 타입 안전성을 확보해도 얻는 것이 없다.
 *
 * <p>{@code ocrResult} 는 DBML 에서 {@code json} 이지만 문자열로 저장한다.
 * H2 와 PostgreSQL 의 JSON 타입 취급이 다르고, 본 구현은 이 값의 내부를
 * 질의하지 않고 통째로 읽기만 한다.
 */
@Entity
@Table(
        name = "documents",
        indexes = @Index(name = "idx_documents_claim_type", columnList = "claim_id, type")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 서류가 속한 청구. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    /** 서류 종류. RECEIPT 와 DETAIL 이 항목 생성의 입력이 된다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentType type;

    /** 업로드된 원본 파일명. 화면 10 에서 근거의 출처로 표시된다. */
    @Column(nullable = false, length = 255)
    private String name;

    /** 파일 접근 주소. 업로드가 구현 범위 밖이라 자리 표시자다. */
    @Column(nullable = false, length = 500)
    private String fileUrl;

    /** 파일 크기(바이트). */
    @Column(nullable = false)
    private Integer sizeBytes;

    /** OCR 처리 상태. PENDING / DONE / FAILED 중 하나다. */
    @Column(nullable = false, length = 20)
    private String ocrStatus;

    /** OCR 추출 결과 원문. JSON 문자열이며 내부를 질의하지 않는다. */
    @Column(columnDefinition = "text")
    private String ocrResult;

    /** 업로드 시각. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    /** OCR 완료 시각. 미처리이면 {@code null}. */
    private LocalDateTime ocrDoneAt;

    /**
     * 제출 서류를 생성한다.
     *
     * @param claim 이 서류가 속한 청구
     * @param type 서류 종류
     * @param name 원본 파일명
     * @param fileUrl 파일 접근 주소
     * @param sizeBytes 파일 크기(바이트)
     * @param ocrStatus OCR 처리 상태. {@code null} 이면 PENDING 으로 채운다
     * @param ocrResult OCR 결과 JSON 문자열. 미처리이면 {@code null}
     * @param ocrDoneAt OCR 완료 시각. 미처리이면 {@code null}
     */
    @Builder
    private Document(Claim claim, DocumentType type, String name, String fileUrl, Integer sizeBytes,
                     String ocrStatus, String ocrResult, LocalDateTime ocrDoneAt) {
        this.claim = claim;
        this.type = type;
        this.name = name;
        this.fileUrl = fileUrl;
        this.sizeBytes = sizeBytes;
        this.ocrStatus = ocrStatus == null ? "PENDING" : ocrStatus;
        this.ocrResult = ocrResult;
        this.ocrDoneAt = ocrDoneAt;
    }
}
