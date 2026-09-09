package com.claimtrace.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.claimtrace.domain.AiRecommendation;
import com.claimtrace.domain.Claim;
import com.claimtrace.domain.ClaimItem;
import com.claimtrace.domain.Intervention;
import com.claimtrace.domain.InterventionPolicy;
import com.claimtrace.domain.Review;
import com.claimtrace.domain.User;
import com.claimtrace.dto.InterventionResponse;
import com.claimtrace.dto.ReviewRequest;
import com.claimtrace.dto.ReviewResponse;
import com.claimtrace.exception.ErrorCode;
import com.claimtrace.exception.InvariantViolationException;
import com.claimtrace.exception.ResourceNotFoundException;
import com.claimtrace.repository.AiRecommendationRepository;
import com.claimtrace.repository.ClaimItemRepository;
import com.claimtrace.repository.InterventionPolicyRepository;
import com.claimtrace.repository.InterventionRepository;
import com.claimtrace.repository.ReviewRepository;

/**
 * 판정 저장 서비스. 규제 대응 단일 진입점 셋 중 첫 번째.
 *
 * <p>항목 판정을 만드는 경로는 이 클래스 하나뿐이다. 우회 경로를 두지 않는
 * 것이 설계의 요구였고, 그래서 개입 기록을 만드는 별도 엔드포인트도 없다.
 *
 * <p><b>한 트랜잭션에서 일어나는 일</b>
 * <ol>
 *   <li>항목과 청구를 읽고 접근 조건을 확인한다 (INV-6, INV-7)</li>
 *   <li>판정 사유가 있는지 확인한다 (INV-2)</li>
 *   <li>최신 AI 권고와 판정을 비교한다 (D-7)</li>
 *   <li>다르면 오버라이드 사유가 있는지 확인한다 (INV-3)</li>
 *   <li>새 판정을 저장한다</li>
 *   <li>이전 판정의 현재 플래그를 내리고 대체 관계를 연결한다 (INV-12, D-6)</li>
 *   <li>오버라이드였다면 개입 기록을 만든다 (INV-3)</li>
 *   <li>개입 정책을 평가해 발동한 정책의 승인 대기 기록을 만든다 (D-5)</li>
 * </ol>
 *
 * <p><b>정책 평가를 확정이 아니라 판정 저장 시점에 두는 이유</b> — 확정
 * 시점에 승인 대기를 만들면, 심사자는 항목을 전부 판정하고 확정을 눌러야
 * 비로소 승인이 필요하다는 것을 알게 되고 그때부터 심사관리자를 기다린다.
 * 판정을 저장할 때 만들면 첫 항목을 판정하는 순간 승인 요청이 올라가고,
 * 나머지 항목을 판정하는 동안 승인이 병렬로 진행된다.
 *
 * <p>기술서 5.5 는 INV-4 의 <b>검사</b>가 확정 시점에만 가능하다고 적었다.
 * 검사는 그대로 확정 서비스에 두고 대기 레코드 생성만 앞당기는 것이므로
 * 문서와 어긋나지 않는다.
 *
 * <p>같은 청구의 여러 항목을 차례로 판정하면 같은 정책이 반복 발동하므로,
 * 이미 기록된 정책은 다시 만들지 않는다.
 *
 * <p><b>검증이 먼저이고 저장이 나중인 이유</b> — 5번 이후로는 실패할 수 없어야
 * 한다. 판정이 저장된 뒤에 개입 기록 생성이 실패하면, 롤백이 걸리더라도
 * 그 사이 다른 요청이 중간 상태를 볼 여지가 생긴다. 애초에 실패 가능한
 * 검사를 전부 앞으로 몰아 두는 편이 명확하다.
 *
 * <p><b>저장 순서가 6번보다 5번이 앞인 이유</b> — 이전 판정의
 * {@code superseded_by} 에 넣을 새 판정의 식별자는 저장 후에야 생긴다.
 * 설계 문서에는 플래그 해제가 먼저 적혀 있지만 구현에서는 순서가 뒤집힌다.
 * 두 작업이 같은 트랜잭션에 있으므로 결과는 같다.
 *
 * <p><b>개입 기록을 심사자가 선언하지 않는다</b> — 3번과 7번이 D-7 의 전부다.
 * 심사자는 판정과 사유만 보내고, 그것이 개입인지 아닌지는 시스템이
 * {@link AiRecommendation#isOverriddenBy} 로 판별한다. 선언 방식이었다면
 * 심사자가 선언하지 않는 우회가 가능하고, 판정 저장과 개입 기록이 다른
 * 트랜잭션이라 원자성도 깨진다.
 */
@Service
public class ReviewService {

    private final ClaimItemRepository claimItemRepository;
    private final ReviewRepository reviewRepository;
    private final AiRecommendationRepository aiRecommendationRepository;
    private final InterventionRepository interventionRepository;
    private final InterventionPolicyRepository interventionPolicyRepository;
    private final PolicyEvaluator policyEvaluator;

    /**
     * 의존성을 주입받는다.
     *
     * @param claimItemRepository 항목 조회
     * @param reviewRepository 판정 조회·저장
     * @param aiRecommendationRepository AI 권고 조회
     * @param interventionRepository 개입 기록 저장
     * @param interventionPolicyRepository 개입 정책 조회
     * @param policyEvaluator 정책 발동 조건 평가
     */
    public ReviewService(ClaimItemRepository claimItemRepository,
                         ReviewRepository reviewRepository,
                         AiRecommendationRepository aiRecommendationRepository,
                         InterventionRepository interventionRepository,
                         InterventionPolicyRepository interventionPolicyRepository,
                         PolicyEvaluator policyEvaluator) {
        this.claimItemRepository = claimItemRepository;
        this.reviewRepository = reviewRepository;
        this.aiRecommendationRepository = aiRecommendationRepository;
        this.interventionRepository = interventionRepository;
        this.interventionPolicyRepository = interventionPolicyRepository;
        this.policyEvaluator = policyEvaluator;
    }

    /**
     * 항목 판정을 저장한다.
     *
     * <p>검증에 실패하면 예외를 던지고 트랜잭션 전체가 롤백된다. 판정만 남고
     * 개입 기록이 빠지는 상태는 만들어지지 않는다.
     *
     * @param itemId 판정할 항목 식별자
     * @param request 판정 내용
     * @param actor 판정을 내리는 심사자
     * @return 저장된 판정. 오버라이드였다면 생성된 개입 기록을 함께 담는다
     * @throws ResourceNotFoundException 항목이 존재하지 않는 경우
     * @throws InvariantViolationException INV-2 · INV-3 · INV-6 · INV-7 을 위반한 경우
     */
    @Transactional
    public ReviewResponse save(Long itemId, ReviewRequest request, User actor) {
        ClaimItem item = claimItemRepository.findWithClaim(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("claimItem", itemId));
        Claim claim = item.getClaim();

        // INV-7 — 확정된 청구는 판정을 바꿀 수 없다.
        if (claim.isDecided()) {
            throw new InvariantViolationException(
                    ErrorCode.CLAIM_ALREADY_DECIDED,
                    Map.of("claimNo", claim.getClaimNo(), "status", claim.getStatus().name()));
        }

        // INV-6 — 본인에게 배정된 청구만 판정할 수 있다.
        if (!claim.isAssignedTo(actor)) {
            throw new InvariantViolationException(
                    ErrorCode.CLAIM_NOT_ASSIGNED,
                    Map.of("claimNo", claim.getClaimNo(), "actorId", actor.getId()));
        }

        // INV-2 — 사유 없는 판정은 저장되지 않는다.
        if (request.reason() == null || request.reason().isBlank()) {
            throw new InvariantViolationException(ErrorCode.REVIEW_REASON_REQUIRED);
        }

        // D-7 — 최신 권고와 비교해 개입 여부를 시스템이 판별한다.
        AiRecommendation latest = aiRecommendationRepository.findLatestByItemId(itemId).orElse(null);
        boolean isOverride = latest != null && latest.isOverriddenBy(request.decision());

        // INV-3 — 권고를 뒤집는 판정에는 사유가 있어야 한다.
        if (isOverride && !hasOverrideReason(request)) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("recommendation", latest.getRecommendation().name());
            details.put("decision", request.decision().name());
            throw new InvariantViolationException(ErrorCode.OVERRIDE_REASON_REQUIRED, details);
        }

        // D-5 — 발동하는 정책을 미리 평가한다. 조건 정의가 깨져 있으면 여기서
        // 예외가 나므로, 저장이 시작되기 전에 실패가 끝난다.
        List<InterventionPolicy> applicablePolicies = evaluatePolicies(claim);

        // 이전 현재 판정을 새 판정 저장 '전에' 읽어 둔다.
        // 저장 후에 읽으면 새 판정도 is_current = true 라 결과가 2건이 되어
        // 단건 조회가 실패한다.
        Optional<Review> previous = reviewRepository.findCurrentByItemId(itemId);

        // 여기서부터는 실패하지 않는다. 검증은 모두 위에서 끝났다.
        Review saved = reviewRepository.save(Review.builder()
                .claimItem(item)
                .reviewer(actor)
                .decision(request.decision())
                .paidAmount(request.paidAmount())
                .reason(request.reason())
                .build());

        // INV-12 · D-6 — 이전 판정은 지우지 않고 플래그만 이관한다.
        // 새 판정을 먼저 저장해야 대체 관계에 넣을 식별자가 생기므로
        // flush 로 식별자를 확정한 뒤 연결한다.
        reviewRepository.flush();
        previous.ifPresent(review -> review.supersededBy(saved));

        InterventionResponse interventionResponse = null;
        if (isOverride) {
            Intervention intervention = interventionRepository.save(Intervention.override(
                    saved, latest, request.overrideReasonType(), request.overrideReason(), actor));
            interventionResponse = InterventionResponse.from(intervention);
        }

        recordRequiredInterventions(claim, applicablePolicies, actor);

        return ReviewResponse.from(saved, interventionResponse);
    }

    /**
     * 이 청구에 발동하는 개입 정책을 찾는다.
     *
     * <p>정책은 항목의 청구금액·담보 분류·최신 권고 확률을 본다. 세 값을
     * 모아 {@link PolicyEvaluator.Target} 으로 만들어 넘기고, 평가기는
     * 엔티티를 모른 채 값만으로 판단한다.
     *
     * @param claim 평가 대상 청구
     * @return 발동하는 정책 목록. 없으면 빈 목록
     */
    private List<InterventionPolicy> evaluatePolicies(Claim claim) {
        List<InterventionPolicy> active = interventionPolicyRepository.findAllActive();
        if (active.isEmpty()) {
            return List.of();
        }

        Map<Long, BigDecimal> probabilities = aiRecommendationRepository
                .findLatestByClaimId(claim.getId()).stream()
                .collect(Collectors.toMap(
                        recommendation -> recommendation.getClaimItem().getId(),
                        AiRecommendation::getExclusionProbability,
                        (first, second) -> first));

        List<PolicyEvaluator.Target> targets = claimItemRepository
                .findAllByClaimIdWithCoverage(claim.getId()).stream()
                .map(item -> new PolicyEvaluator.Target(
                        item.getId(),
                        item.getClaimedAmount(),
                        item.getCoverage().getType(),
                        probabilities.get(item.getId())))
                .toList();

        return policyEvaluator.findApplicable(active, targets);
    }

    /**
     * 발동한 정책의 승인 대기 개입을 기록한다.
     *
     * <p>이미 같은 정책으로 기록된 개입이 있으면 건너뛴다. 같은 청구의 여러
     * 항목을 차례로 판정하면 같은 정책이 반복 발동하는데, 그때마다 대기
     * 레코드를 만들면 심사관리자가 같은 건을 여러 번 승인해야 하고 하나만
     * 승인되면 나머지가 대기로 남아 INV-4 가 확정을 계속 막는다.
     *
     * @param claim 대상 청구
     * @param policies 발동한 정책 목록
     * @param actor 판정을 내린 심사자
     */
    private void recordRequiredInterventions(Claim claim, List<InterventionPolicy> policies, User actor) {
        for (InterventionPolicy policy : policies) {
            if (interventionRepository.existsByClaimIdAndPolicyId(claim.getId(), policy.getId())) {
                continue;
            }
            String reason = "정책 " + policy.getCode() + "(" + policy.getName()
                    + ") 조건에 해당해 " + policy.getRequiredIntervention().getLabel() + "이 요구되었습니다.";
            interventionRepository.save(Intervention.required(claim, policy, reason, actor));
        }
    }

    /**
     * 항목의 판정 이력을 최신순으로 조회한다.
     *
     * <p>대체된 판정도 모두 포함한다. 이의제기로 판정이 뒤집힌 경우 최초에
     * 왜 그렇게 판단했는지가 이 목록에 남아 있다(D-6).
     *
     * @param itemId 항목 식별자
     * @return 판정 시각 내림차순의 판정 목록
     * @throws ResourceNotFoundException 항목이 존재하지 않는 경우
     */
    @Transactional(readOnly = true)
    public List<ReviewResponse> findHistory(Long itemId) {
        if (claimItemRepository.findWithClaim(itemId).isEmpty()) {
            throw new ResourceNotFoundException("claimItem", itemId);
        }
        return reviewRepository.findHistoryByItemId(itemId).stream()
                .map(ReviewResponse::from)
                .toList();
    }

    /**
     * 오버라이드 사유가 요청에 갖추어져 있는지 확인한다.
     *
     * <p>유형과 본문이 모두 있어야 한다. 유형만 고르고 본문을 비우면
     * 집계는 가능하지만 "왜 그렇게 판단했는가"가 남지 않고, 본문만 쓰면
     * 화면 13 의 사유별 집계가 불가능해진다. 보조수단성 ⑧이 요구하는 것은
     * 개입 사실의 기록이 아니라 그 내용의 기록이다.
     *
     * @param request 판정 요청
     * @return 유형과 본문이 모두 채워져 있으면 {@code true}
     */
    private boolean hasOverrideReason(ReviewRequest request) {
        return request.overrideReasonType() != null
                && request.overrideReason() != null
                && !request.overrideReason().isBlank();
    }
}
