package com.claimtrace.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.claimtrace.domain.Claim;
import com.claimtrace.domain.ClaimItem;
import com.claimtrace.domain.Document;
import com.claimtrace.domain.Evidence;
import com.claimtrace.domain.User;
import com.claimtrace.domain.enums.DisclosureLevel;
import com.claimtrace.dto.EvidenceCreateRequest;
import com.claimtrace.dto.EvidenceResponse;
import com.claimtrace.dto.EvidenceStatusRequest;
import com.claimtrace.exception.ErrorCode;
import com.claimtrace.exception.InvariantViolationException;
import com.claimtrace.exception.ResourceNotFoundException;
import com.claimtrace.repository.ClaimItemRepository;
import com.claimtrace.repository.DocumentRepository;
import com.claimtrace.repository.EvidenceRepository;

/**
 * 근거 검토 서비스.
 *
 * <p>근거의 추가, 채택, 기각을 처리한다. 판정 저장·확정·설명문 생성과 달리 설계가
 * 규정한 "단일 진입점" 셋에 들어가지는 않지만, INV-7 과 INV-11 을 강제하는
 * 자리라 같은 방식으로 만든다.
 *
 * <p><b>기각된 근거를 지우지 않는다</b>(D-3). 상태만 {@code REJECTED} 로
 * 바꾸고 사유를 함께 남긴다. RACI 참고사항이 채택·미채택 사유 기재를
 * 의무로 두고 있고, 이의제기 재검토 시 "무엇을 검토했고 왜 배제했는가"가
 * 필요하기 때문이다.
 *
 * <p><b>{@code GENERATED} 로 되돌리는 전이는 허용하지 않는다.</b> 설계 문서는
 * 미검토에서 채택 또는 기각으로 가는 전이만 규정했고, 검토를 취소하는 경로는
 * 정의하지 않았다. 요청 스키마에는 {@code GENERATED} 가 값으로 존재하므로
 * 명시적으로 거부한다. 정의되지 않은 전이를 임의로 허용하면 "언제 검토가
 * 이루어졌는가"의 기록이 흐려진다.
 */
@Service
public class EvidenceService {

    private final EvidenceRepository evidenceRepository;
    private final ClaimItemRepository claimItemRepository;
    private final DocumentRepository documentRepository;

    /**
     * 의존성을 주입받는다.
     *
     * @param evidenceRepository 근거 조회·저장
     * @param claimItemRepository 항목 조회
     * @param documentRepository 근거가 지목한 서류 확인
     */
    public EvidenceService(EvidenceRepository evidenceRepository,
                           ClaimItemRepository claimItemRepository,
                           DocumentRepository documentRepository) {
        this.evidenceRepository = evidenceRepository;
        this.claimItemRepository = claimItemRepository;
        this.documentRepository = documentRepository;
    }

    /**
     * 심사자가 근거를 직접 추가한다.
     *
     * <p>사람이 판단해 넣은 것이므로 생성 즉시 채택 상태다. 자기가 추가한
     * 근거를 다시 채택하게 하는 것은 의미 없는 절차다.
     *
     * <p>공개 수준이 고객용인데 고객용 문구가 없으면 거부한다. 설계 문서가
     * 규정하지 않았던 조건인데, 그대로 두면 심사자는 근거를 공개했다고
     * 믿지만 설명문 생성 시 조용히 걸러진다. 오류가 아니라 침묵으로
     * 나타나는 문제라 생성 시점에 막는다.
     *
     * @param itemId 근거를 추가할 항목 식별자
     * @param request 근거 내용
     * @param actor 근거를 추가하는 심사자
     * @return 생성된 근거
     * @throws ResourceNotFoundException 항목이나 서류가 존재하지 않는 경우
     * @throws InvariantViolationException INV-6 · INV-7 을 위반하거나 고객용 문구가 없는 경우
     */
    @Transactional
    public EvidenceResponse create(Long itemId, EvidenceCreateRequest request, User actor) {
        ClaimItem item = claimItemRepository.findWithClaim(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("claimItem", itemId));
        Claim claim = item.getClaim();

        verifyModifiable(claim, actor, Map.of("claimItemId", itemId));

        if (request.disclosureLevel() == DisclosureLevel.CUSTOMER
                && (request.contentCustomer() == null || request.contentCustomer().isBlank())) {
            throw new InvariantViolationException(
                    ErrorCode.CUSTOMER_CONTENT_REQUIRED, Map.of("claimItemId", itemId));
        }

        Document document = null;
        if (request.documentId() != null) {
            document = documentRepository.findByIdAndClaimId(request.documentId(), claim.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("document", request.documentId()));
        }

        Evidence evidence = evidenceRepository.save(Evidence.manual(
                item, request.polarity(), request.disclosureLevel(),
                request.contentInternal(), request.contentCustomer(),
                document, request.targetAmount(), actor, LocalDateTime.now()));

        return EvidenceResponse.from(evidence);
    }

    /**
     * 근거를 변경할 수 있는 상태인지 확인한다.
     *
     * <p>근거 추가와 상태 전이가 같은 조건을 요구하므로 한곳에 모은다.
     * 두 곳에 따로 적으면 한쪽에만 조건이 추가되는 일이 생긴다.
     *
     * @param claim 대상 청구
     * @param actor 요청한 심사자
     * @param context 오류 응답에 실을 추가 정보
     * @throws InvariantViolationException INV-6 또는 INV-7 을 위반한 경우
     */
    private void verifyModifiable(Claim claim, User actor, Map<String, Object> context) {
        // INV-7 — 확정·종결된 청구의 근거는 변경되지 않는다.
        if (claim.isLocked()) {
            Map<String, Object> details = new LinkedHashMap<>(context);
            details.put("claimNo", claim.getClaimNo());
            details.put("status", claim.getStatus().name());
            throw new InvariantViolationException(ErrorCode.CLAIM_ALREADY_DECIDED, details);
        }

        // INV-6 — 본인에게 배정된 청구의 근거만 다룰 수 있다.
        if (!claim.isAssignedTo(actor)) {
            Map<String, Object> details = new LinkedHashMap<>(context);
            details.put("claimNo", claim.getClaimNo());
            details.put("actorId", actor.getId());
            throw new InvariantViolationException(ErrorCode.CLAIM_NOT_ASSIGNED, details);
        }
    }

    /**
     * 근거의 상태를 전이시킨다.
     *
     * <p>기각으로 전이할 때는 사유 유형이 반드시 있어야 한다(INV-11).
     * 확정된 청구의 근거는 어떤 전이도 할 수 없다(INV-7).
     *
     * @param evidenceId 근거 식별자
     * @param request 전이할 상태와 사유
     * @param actor 검토를 수행하는 심사자
     * @return 변경된 근거
     * @throws ResourceNotFoundException 근거가 존재하지 않는 경우
     * @throws InvariantViolationException INV-6 · INV-7 · INV-11 을 위반한 경우
     */
    @Transactional
    public EvidenceResponse changeStatus(Long evidenceId, EvidenceStatusRequest request, User actor) {
        Evidence evidence = evidenceRepository.findWithClaim(evidenceId)
                .orElseThrow(() -> new ResourceNotFoundException("evidence", evidenceId));
        Claim claim = evidence.getClaimItem().getClaim();

        verifyModifiable(claim, actor, Map.of("evidenceId", evidenceId));

        LocalDateTime now = LocalDateTime.now();
        switch (request.status()) {
            case ADOPTED -> evidence.adopt(actor, now);
            case REJECTED -> {
                // INV-11 — 기각된 근거에는 기각 사유가 있다.
                if (request.rejectionReasonType() == null) {
                    throw new InvariantViolationException(
                            ErrorCode.REJECTION_REASON_REQUIRED, Map.of("evidenceId", evidenceId));
                }
                evidence.reject(request.rejectionReasonType(), request.rejectionNote(), actor, now);
            }
            case GENERATED -> throw new InvariantViolationException(
                    ErrorCode.INVALID_REQUEST,
                    Map.of("detail", "검토를 취소해 미검토 상태로 되돌리는 전이는 설계에 정의되어 있지 않습니다"));
        }

        if (request.disclosureLevel() != null) {
            evidence.changeDisclosureLevel(request.disclosureLevel());
        }

        return EvidenceResponse.from(evidence);
    }

    /**
     * 항목의 근거를 모두 조회한다.
     *
     * <p>기각된 근거도 포함한다. 화면 10 이 채택·기각·미검토를 함께 보여주고,
     * 하단에 채택된 부정 근거 수를 표시해 INV-5 충족 여부를 미리 알리기
     * 때문이다.
     *
     * @param itemId 항목 식별자
     * @return 생성 순서의 근거 목록
     * @throws ResourceNotFoundException 항목이 존재하지 않는 경우
     */
    @Transactional(readOnly = true)
    public List<EvidenceResponse> findByItem(Long itemId) {
        if (claimItemRepository.findWithClaim(itemId).isEmpty()) {
            throw new ResourceNotFoundException("claimItem", itemId);
        }
        return evidenceRepository.findAllByItemId(itemId).stream()
                .map(EvidenceResponse::from)
                .toList();
    }
}
