package com.claimtrace.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.claimtrace.domain.Claim;
import com.claimtrace.domain.ClaimItem;
import com.claimtrace.domain.Evidence;
import com.claimtrace.domain.Explanation;
import com.claimtrace.domain.Review;
import com.claimtrace.domain.User;
import com.claimtrace.domain.enums.Polarity;
import com.claimtrace.dto.ExplanationResponse;
import com.claimtrace.exception.ErrorCode;
import com.claimtrace.exception.InvariantViolationException;
import com.claimtrace.exception.ResourceNotFoundException;
import com.claimtrace.repository.ClaimItemRepository;
import com.claimtrace.repository.ClaimRepository;
import com.claimtrace.repository.EvidenceRepository;
import com.claimtrace.repository.ExplanationRepository;
import com.claimtrace.repository.ReviewRepository;

/**
 * 고객 설명문 초안 생성 서비스. 규제 대응 단일 진입점 셋 중 세 번째.
 *
 * <p>두 불변조건이 여기서 만난다.
 * <ul>
 *   <li>INV-5 — 부지급·일부지급 항목에는 고객용 부정 근거가 1건 이상 있어야
 *       한다. 지급하지 않겠다고 하면서 그 이유를 대지 못하는 설명문을 막는다.</li>
 *   <li>INV-9 — 고객 응답에 내부용 근거가 섞이지 않는다. 새플리 기여도가
 *       그대로 고객에게 나가는 경로를 없앤다.</li>
 * </ul>
 *
 * <p><b>두 조건을 같은 메서드로 판별한다.</b> 재료를 고를 때와 부정 근거
 * 개수를 셀 때 모두 {@link Evidence#isUsableInCustomerExplanation()} 을 쓴다.
 * 기준이 두 곳에 따로 적히면, 검사는 통과했는데 정작 본문에는 그 근거가
 * 들어가지 않는 상태가 생길 수 있다.
 *
 * <p><b>설명서 레코드를 새로 만들지 않는다.</b> 기술서 5.5 는 제공 기한 산정을
 * 청구인의 신청 API 책임으로 규정했고(INV-8), 그 API 는 고객 포털에 속해
 * 구현 범위 밖이다. 따라서 이 서비스는 이미 접수된 설명서를 찾아 본문을
 * 채운다. 여기서 기한을 임의로 계산해 레코드를 만들면 설계가 정한 책임
 * 경계를 구현이 넘어서게 된다.
 *
 * <p><b>초안 생성이 발급이 아니다.</b> 상태는 {@code GENERATING} 까지만
 * 간다. 심사자가 검토하고 확정해야 {@code PROVIDED} 가 되며, 완전 자동
 * 발급 경로는 두지 않았다. 보조수단성 원칙이 요구하는 사람의 최종 확인이
 * 여기서 빠지면 이 시스템의 전제가 무너진다.
 */
@Service
public class ExplanationService {

    private final ClaimRepository claimRepository;
    private final ClaimItemRepository claimItemRepository;
    private final ReviewRepository reviewRepository;
    private final EvidenceRepository evidenceRepository;
    private final ExplanationRepository explanationRepository;

    /**
     * 의존성을 주입받는다.
     *
     * @param claimRepository 청구 조회
     * @param claimItemRepository 항목 조회
     * @param reviewRepository 현재 판정 조회
     * @param evidenceRepository 근거 조회
     * @param explanationRepository 설명서 조회
     */
    public ExplanationService(ClaimRepository claimRepository,
                              ClaimItemRepository claimItemRepository,
                              ReviewRepository reviewRepository,
                              EvidenceRepository evidenceRepository,
                              ExplanationRepository explanationRepository) {
        this.claimRepository = claimRepository;
        this.claimItemRepository = claimItemRepository;
        this.reviewRepository = reviewRepository;
        this.evidenceRepository = evidenceRepository;
        this.explanationRepository = explanationRepository;
    }

    /**
     * 고객 설명문 초안을 생성한다.
     *
     * @param claimId 대상 청구 식별자
     * @param actor 초안을 생성하는 심사자
     * @return 본문이 채워진 설명서
     * @throws ResourceNotFoundException 청구가 없거나 접수된 국소 설명서가 없는 경우
     * @throws InvariantViolationException INV-5 · INV-6 을 위반하거나 미판정 항목이 있는 경우
     */
    @Transactional
    public ExplanationResponse draft(Long claimId, User actor) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("claim", claimId));

        // INV-6 — 본인에게 배정된 청구의 설명문만 작성할 수 있다.
        if (!claim.isAssignedTo(actor)) {
            throw new InvariantViolationException(
                    ErrorCode.CLAIM_NOT_ASSIGNED,
                    Map.of("claimNo", claim.getClaimNo(), "actorId", actor.getId()));
        }

        Explanation explanation = explanationRepository.findLocalByClaimId(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("localExplanation", claimId));

        List<ClaimItem> items = claimItemRepository.findAllByClaimIdWithCoverage(claimId);
        Map<Long, Review> reviews = indexReviews(reviewRepository.findCurrentByClaimId(claimId));
        Map<Long, List<Evidence>> customerEvidences = indexCustomerEvidences(claimId);

        requireAllItemsReviewed(items, reviews);
        requireNegativeEvidence(items, reviews, customerEvidences);

        explanation.applyDraft(composeBody(claim, items, reviews, customerEvidences));
        return ExplanationResponse.from(explanation);
    }

    /**
     * 현재 판정을 항목 식별자로 색인한다.
     *
     * @param reviews 현재 판정 목록
     * @return 항목 식별자를 키로 하는 맵
     */
    private Map<Long, Review> indexReviews(List<Review> reviews) {
        Map<Long, Review> indexed = new LinkedHashMap<>();
        for (Review review : reviews) {
            indexed.putIfAbsent(review.getClaimItem().getId(), review);
        }
        return indexed;
    }

    /**
     * 고객 설명문에 쓸 수 있는 근거만 항목별로 모은다.
     *
     * <p>INV-9 가 강제되는 지점이다. 채택되었고, 공개 수준이 고객용이며,
     * 고객용 문구가 실제로 채워진 근거만 남는다. 내부용 근거는 이 시점에
     * 걸러지므로 이후 어느 경로로도 본문에 들어갈 수 없다.
     *
     * @param claimId 청구 식별자
     * @return 항목 식별자를 키로 하는 근거 목록
     */
    private Map<Long, List<Evidence>> indexCustomerEvidences(Long claimId) {
        Map<Long, List<Evidence>> indexed = new LinkedHashMap<>();
        for (Evidence evidence : evidenceRepository.findAllByClaimId(claimId)) {
            if (evidence.isUsableInCustomerExplanation()) {
                indexed.computeIfAbsent(evidence.getClaimItem().getId(), key -> new ArrayList<>())
                        .add(evidence);
            }
        }
        return indexed;
    }

    /**
     * 모든 항목이 판정되었는지 확인한다.
     *
     * <p>새 불변조건이 아니라 실무상의 전제다. 판정되지 않은 항목은 설명할
     * 내용 자체가 없고, 그 항목을 빼고 설명문을 만들면 고객이 받은 문서에서
     * 항목 하나가 통째로 사라진다. INV-10 과 같은 오류 코드를 쓴다.
     *
     * @param items 청구의 항목 목록
     * @param reviews 항목별 현재 판정
     * @throws InvariantViolationException 미판정 항목이 있는 경우
     */
    private void requireAllItemsReviewed(List<ClaimItem> items, Map<Long, Review> reviews) {
        List<Long> pending = items.stream()
                .map(ClaimItem::getId)
                .filter(id -> !reviews.containsKey(id))
                .toList();
        if (!pending.isEmpty()) {
            throw new InvariantViolationException(
                    ErrorCode.PENDING_ITEMS_EXIST, Map.of("pendingItemIds", pending));
        }
    }

    /**
     * 부지급·일부지급 항목에 고객용 부정 근거가 있는지 확인한다.
     *
     * <p>INV-5 의 강제 지점이다. 항목을 순서대로 검사하고 처음 걸린 항목에서
     * 멈춘다. 전부 모아 반환하지 않는 이유는, 심사자가 화면 10 에서 근거를
     * 하나 채택하고 다시 시도하는 흐름이 자연스럽기 때문이다. 한 번에 여러
     * 항목을 나열하면 어느 것부터 손대야 하는지가 오히려 흐려진다.
     *
     * @param items 청구의 항목 목록
     * @param reviews 항목별 현재 판정
     * @param customerEvidences 항목별 고객용 근거
     * @throws InvariantViolationException 부정 근거가 없는 항목이 있는 경우
     */
    private void requireNegativeEvidence(List<ClaimItem> items, Map<Long, Review> reviews,
                                         Map<Long, List<Evidence>> customerEvidences) {
        for (ClaimItem item : items) {
            Review review = reviews.get(item.getId());
            if (!review.getDecision().requiresNegativeEvidence()) {
                continue;
            }
            long negativeCount = customerEvidences.getOrDefault(item.getId(), List.of()).stream()
                    .filter(evidence -> evidence.getPolarity() == Polarity.NEGATIVE)
                    .count();
            if (negativeCount == 0) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("itemId", item.getId());
                details.put("itemName", item.getName());
                details.put("decision", review.getDecision().name());
                details.put("customerNegativeCount", 0);
                throw new InvariantViolationException(ErrorCode.NEGATIVE_EVIDENCE_REQUIRED, details);
            }
        }
    }

    /**
     * 초안 본문을 조립한다.
     *
     * <p>항목별로 판정과 근거 문구를 나열하는 형식이다. 인사말이나 맺음말을
     * 붙여 안내문처럼 꾸미지 않았다. 이 시스템이 보여야 하는 것은 "고객용으로
     * 표시된 채택 근거만 설명문에 들어간다"이지 문안의 완성도가 아니고,
     * 지금 형식이면 본문의 어느 문장이 어느 근거에서 나왔는지 눈으로 짚을 수
     * 있다. 문안 다듬기는 심사자가 화면 11 에서 한다.
     *
     * @param claim 대상 청구
     * @param items 항목 목록
     * @param reviews 항목별 현재 판정
     * @param customerEvidences 항목별 고객용 근거
     * @return 조립된 본문
     */
    private String composeBody(Claim claim, List<ClaimItem> items, Map<Long, Review> reviews,
                               Map<Long, List<Evidence>> customerEvidences) {
        StringBuilder body = new StringBuilder();
        body.append("청구번호 ").append(claim.getClaimNo()).append('\n');
        body.append("진료기관 ").append(claim.getHospitalName()).append('\n');
        body.append("진료기간 ").append(claim.getTreatedFrom()).append(" ~ ").append(claim.getTreatedTo());
        body.append("\n\n");
        body.append("청구하신 항목별 심사 결과와 그 사유를 알려드립니다.\n");

        for (ClaimItem item : items) {
            Review review = reviews.get(item.getId());
            body.append('\n');
            body.append("■ ").append(item.getSeq()).append("번 항목 · ").append(item.getName())
                    .append(" — ").append(review.getDecision().getLabel())
                    .append(" (").append(String.format("%,d", review.getPaidAmount())).append("원)\n");

            List<Evidence> evidences = customerEvidences.getOrDefault(item.getId(), List.of());
            if (evidences.isEmpty()) {
                body.append("  · 별도로 안내드릴 사유가 없습니다.\n");
                continue;
            }
            for (Evidence evidence : evidences) {
                body.append("  · ").append(evidence.getContentCustomer()).append('\n');
            }
        }
        return body.toString();
    }
}
