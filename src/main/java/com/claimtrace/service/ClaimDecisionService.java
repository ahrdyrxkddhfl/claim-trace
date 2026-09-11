package com.claimtrace.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.claimtrace.domain.Claim;
import com.claimtrace.domain.ClaimItem;
import com.claimtrace.domain.Intervention;
import com.claimtrace.domain.Review;
import com.claimtrace.domain.User;
import com.claimtrace.dto.DecisionResult;
import com.claimtrace.exception.ErrorCode;
import com.claimtrace.exception.InvariantViolationException;
import com.claimtrace.exception.ResourceNotFoundException;
import com.claimtrace.repository.ClaimItemRepository;
import com.claimtrace.repository.ClaimRepository;
import com.claimtrace.repository.InterventionRepository;
import com.claimtrace.repository.ReviewRepository;
import com.claimtrace.domain.enums.InterventionType;

/**
 * 판정 확정 서비스. 규제 대응 단일 진입점 셋 중 두 번째.
 *
 * <p>청구 전체를 봐야 판단할 수 있는 두 불변조건이 여기 있다.
 * <ul>
 *   <li>INV-10 — 모든 항목에 현재 판정이 있어야 확정할 수 있다</li>
 *   <li>INV-4 — 정책이 요구한 개입이 승인되어야 확정할 수 있다</li>
 * </ul>
 *
 * <p>둘 다 항목 하나나 판정 하나만 보아서는 판별되지 않는다. 판정 저장
 * 시점으로 앞당길 수 없는 검사이며, 그래서 확정이라는 별도의 진입점이
 * 존재한다.
 *
 * <p><b>정책 발동 여부는 여기서 다시 평가하지 않는다.</b> 판정을 저장할 때
 * 이미 평가해 승인 대기 개입을 기록해 두었으므로, 확정은 그 개입들이
 * 해소되었는지만 본다. 확정 시점에 다시 평가하면 정책이 그 사이에 수정된
 * 경우 판정 당시와 다른 기준이 적용되는데, 화면 12 는 "조건 변경이 기존
 * 판정에 소급 적용되지 않는다"고 규정했다. 기록된 개입을 보는 방식이
 * 그 규정을 자연스럽게 지킨다.
 *
 * <p><b>확정 이후에는 되돌릴 수 없다.</b> 상태가 {@code DECIDED} 가 되면
 * INV-7 이 근거와 판정의 변경을 막는다. 이의제기가 들어오면 상태가
 * {@code OBJECTION} 으로 바뀌고 재검토가 새 판정으로 쌓인다(D-6).
 */
@Service
public class ClaimDecisionService {

    private final ClaimRepository claimRepository;
    private final ClaimItemRepository claimItemRepository;
    private final ReviewRepository reviewRepository;
    private final InterventionRepository interventionRepository;

    /**
     * 의존성을 주입받는다.
     *
     * @param claimRepository 청구 조회
     * @param claimItemRepository 항목 조회
     * @param reviewRepository 현재 판정 조회
     * @param interventionRepository 개입 이력 조회
     */
    public ClaimDecisionService(ClaimRepository claimRepository,
                                ClaimItemRepository claimItemRepository,
                                ReviewRepository reviewRepository,
                                InterventionRepository interventionRepository) {
        this.claimRepository = claimRepository;
        this.claimItemRepository = claimItemRepository;
        this.reviewRepository = reviewRepository;
        this.interventionRepository = interventionRepository;
    }

    /**
     * 청구의 판정을 확정한다.
     *
     * @param claimId 확정할 청구 식별자
     * @param actor 확정을 수행하는 심사자
     * @return 확정 결과
     * @throws ResourceNotFoundException 청구가 존재하지 않는 경우
     * @throws InvariantViolationException INV-4 · INV-6 · INV-7 · INV-10 을 위반한 경우
     */
    @Transactional
    public DecisionResult decide(Long claimId, User actor) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("claim", claimId));

        // 이미 확정·종결된 청구를 다시 확정할 수 없다.
        if (claim.isLocked()) {
            throw new InvariantViolationException(
                    ErrorCode.CLAIM_ALREADY_DECIDED,
                    Map.of("claimNo", claim.getClaimNo(), "decidedAt", String.valueOf(claim.getDecidedAt())));
        }

        // INV-6 — 본인에게 배정된 청구만 확정할 수 있다.
        if (!claim.isAssignedTo(actor)) {
            throw new InvariantViolationException(
                    ErrorCode.CLAIM_NOT_ASSIGNED,
                    Map.of("claimNo", claim.getClaimNo(), "actorId", actor.getId()));
        }

        List<ClaimItem> items = claimItemRepository.findAllByClaimIdWithCoverage(claimId);
        Map<Long, Review> currentReviews = indexByItemId(reviewRepository.findCurrentByClaimId(claimId));

        // INV-10 — 미판정 항목이 남아 있으면 확정할 수 없다.
        List<Long> pendingItemIds = new ArrayList<>();
        for (ClaimItem item : items) {
            if (!currentReviews.containsKey(item.getId())) {
                pendingItemIds.add(item.getId());
            }
        }
        if (!pendingItemIds.isEmpty()) {
            throw new InvariantViolationException(
                    ErrorCode.PENDING_ITEMS_EXIST, Map.of("pendingItemIds", pendingItemIds));
        }

        // INV-4 — 승인되지 않은 개입이 남아 있으면 단독으로 확정할 수 없다.
        List<Intervention> blocking = interventionRepository.findAllByClaimId(claimId).stream()
                .filter(Intervention::blocksDecision)
                .toList();
        if (!blocking.isEmpty()) {
            throw new InvariantViolationException(
                    ErrorCode.DUAL_CHECK_REQUIRED, describeBlocking(blocking));
        }

        LocalDateTime now = LocalDateTime.now();
        claim.decide(now);

        return buildResult(claim, items, currentReviews, now);
    }

    /**
     * 현재 판정 목록을 항목 식별자로 색인한다.
     *
     * <p>항목 수만큼 조회를 반복하지 않기 위해 한 번에 읽고 맵으로 바꾼다.
     * 같은 항목에 현재 판정이 둘 이상 있으면 INV-12 가 깨진 것이지만, 여기서
     * 예외를 던지지는 않고 먼저 읽은 것을 남긴다. INV-12 의 검증은 판정 저장
     * 서비스와 그 테스트의 몫이고, 확정 서비스가 그 책임까지 지면 검사 지점이
     * 흩어진다.
     *
     * @param reviews 현재 판정 목록
     * @return 항목 식별자를 키로 하는 맵
     */
    private Map<Long, Review> indexByItemId(List<Review> reviews) {
        Map<Long, Review> indexed = new LinkedHashMap<>();
        for (Review review : reviews) {
            indexed.putIfAbsent(review.getClaimItem().getId(), review);
        }
        return indexed;
    }

    /**
     * 확정을 막고 있는 개입을 응답 상세로 옮긴다.
     *
     * <p>어느 정책 때문에 막혔는지 알려주지 않으면 심사자는 화면 13 의 어느
     * 건을 승인해 달라고 요청해야 하는지 알 수 없다.
     *
     * @param blocking 확정을 막는 개입 목록
     * @return 정책 코드와 개입 유형을 담은 상세
     */
    private Map<String, Object> describeBlocking(List<Intervention> blocking) {
        List<String> policyCodes = blocking.stream()
                .map(Intervention::getInterventionPolicy)
                .filter(policy -> policy != null)
                .map(policy -> policy.getCode())
                .toList();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("policyCode", policyCodes.isEmpty() ? null : policyCodes.get(0));
        details.put("policyCodes", policyCodes);
        details.put("pendingInterventionIds", blocking.stream().map(Intervention::getId).toList());
        return details;
    }

    /**
     * 확정 결과를 만든다.
     *
     * <p>지급 총액은 저장된 값이 아니라 현재 판정들의 합으로 계산한다.
     * 오버라이드 건수는 개입 이력에서 센다. 두 값 모두 파생 값이며 별도
     * 컬럼으로 두지 않는 이유는, 원본이 바뀔 때마다 갱신해야 하는 값을
     * 늘리면 어긋날 자리가 늘기 때문이다.
     *
     * @param claim 확정된 청구
     * @param items 청구의 항목 목록
     * @param currentReviews 항목별 현재 판정
     * @param decidedAt 확정 시각
     * @return 확정 결과
     */
    private DecisionResult buildResult(Claim claim, List<ClaimItem> items,
                                       Map<Long, Review> currentReviews, LocalDateTime decidedAt) {
        List<DecisionResult.ItemDecisionSummary> summaries = new ArrayList<>();
        int paidTotal = 0;
        for (ClaimItem item : items) {
            Review review = currentReviews.get(item.getId());
            summaries.add(new DecisionResult.ItemDecisionSummary(
                    item.getId(), review.getDecision(), review.getPaidAmount()));
            paidTotal += review.getPaidAmount();
        }

        long overrideCount = interventionRepository
                .countByClaimIdAndType(claim.getId(), InterventionType.OVERRIDE);

        return new DecisionResult(
                claim.getId(), claim.getStatus(), paidTotal, decidedAt, summaries, overrideCount);
    }
}
