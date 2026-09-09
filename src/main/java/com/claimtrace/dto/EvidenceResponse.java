package com.claimtrace.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.claimtrace.domain.Evidence;
import com.claimtrace.domain.enums.DisclosureLevel;
import com.claimtrace.domain.enums.EvidenceSource;
import com.claimtrace.domain.enums.EvidenceStatus;
import com.claimtrace.domain.enums.Polarity;
import com.claimtrace.domain.enums.RejectionReasonType;

/**
 * 근거 응답. 화면 10 의 근거 카드에 대응한다.
 *
 * <p>기각된 근거도 목록에 그대로 실린다(D-3). 삭제하지 않는 이유는 채택·미채택
 * 사유 기재가 의무이고, 이의제기 재검토 시 "무엇을 검토했고 왜 배제했는가"가
 * 필요하기 때문이다. 응답에서 걸러내면 보존하는 의미가 없다.
 *
 * <p><b>이 응답은 심사자용이다.</b> {@code contentInternal} 과 AI 기여도가
 * 그대로 실린다. 고객용 응답은 {@code disclosureLevel} 로 걸러진 다른 형태이며
 * (INV-9), 하나의 DTO 에 노출 수준 분기를 넣지 않는다. 분기를 두면 어느
 * 호출에서 어느 필드가 나가는지 추적하기 어려워지고, 실수 하나가 곧
 * 내부 정보 유출이 된다.
 *
 * @param id 근거 식별자
 * @param source 생성 출처
 * @param polarity 근거가 가리키는 방향
 * @param status 검토 상태
 * @param disclosureLevel 공개 수준
 * @param contentInternal 심사자용 문구
 * @param contentCustomer 고객용 문구. 없으면 {@code null}
 * @param contribution 새플리 기여도. AI 근거가 아니면 {@code null}
 * @param targetAmount 영향 금액(원). 없으면 {@code null}
 * @param rule 출처 룰. 룰 근거가 아니면 {@code null}
 * @param documentName 근거가 된 서류명. 없으면 {@code null}
 * @param rejectionReasonType 기각 사유 유형. 기각 상태가 아니면 {@code null}
 * @param rejectionNote 기각 상세 사유. 없으면 {@code null}
 * @param decidedBy 채택·기각을 수행한 심사자 성명. 미검토이면 {@code null}
 * @param decidedAt 채택·기각 시각. 미검토이면 {@code null}
 */
public record EvidenceResponse(
        Long id,
        EvidenceSource source,
        Polarity polarity,
        EvidenceStatus status,
        DisclosureLevel disclosureLevel,
        String contentInternal,
        String contentCustomer,
        BigDecimal contribution,
        Integer targetAmount,
        RuleRef rule,
        String documentName,
        RejectionReasonType rejectionReasonType,
        String rejectionNote,
        String decidedBy,
        LocalDateTime decidedAt) {

    /**
     * 출처 룰의 요약.
     *
     * <p>{@code version} 을 함께 싣는 것이 중요하다. 근거는 룰의 특정 버전을
     * 가리키므로, 룰이 수정된 뒤에 이 근거를 다시 조회해도 판정 당시의
     * 조항 원문이 그대로 나온다.
     *
     * @param code 룰 코드
     * @param version 버전 번호
     * @param articleNo 약관 조항
     * @param articleText 조항 원문
     */
    public record RuleRef(String code, Short version, String articleNo, String articleText) {
    }

    /**
     * 엔티티를 응답으로 변환한다.
     *
     * <p>지연 로딩된 연관을 읽으므로 트랜잭션 안에서 호출해야 한다.
     *
     * @param evidence 변환할 근거
     * @return 근거 응답
     */
    public static EvidenceResponse from(Evidence evidence) {
        RuleRef ruleRef = evidence.getRule() == null ? null : new RuleRef(
                evidence.getRule().getCode(),
                evidence.getRule().getVersion(),
                evidence.getRule().getArticleNo(),
                evidence.getRule().getArticleText());

        return new EvidenceResponse(
                evidence.getId(),
                evidence.getSource(),
                evidence.getPolarity(),
                evidence.getStatus(),
                evidence.getDisclosureLevel(),
                evidence.getContentInternal(),
                evidence.getContentCustomer(),
                evidence.getContribution(),
                evidence.getTargetAmount(),
                ruleRef,
                evidence.getDocument() == null ? null : evidence.getDocument().getName(),
                evidence.getRejectionReasonType(),
                evidence.getRejectionNote(),
                evidence.getDecidedBy() == null ? null : evidence.getDecidedBy().getName(),
                evidence.getDecidedAt());
    }
}
