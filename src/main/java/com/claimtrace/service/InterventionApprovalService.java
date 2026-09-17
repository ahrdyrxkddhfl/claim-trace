package com.claimtrace.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.claimtrace.domain.Claim;
import com.claimtrace.domain.Intervention;
import com.claimtrace.domain.InterventionRule;
import com.claimtrace.domain.Review;
import com.claimtrace.domain.User;
import com.claimtrace.dto.DualCheckRequest;
import com.claimtrace.dto.InterventionResponse;
import com.claimtrace.exception.ErrorCode;
import com.claimtrace.exception.InvariantViolationException;
import com.claimtrace.exception.ResourceNotFoundException;
import com.claimtrace.repository.ClaimRepository;
import com.claimtrace.repository.InterventionRepository;
import com.claimtrace.repository.ReviewRepository;

/**
 * 복수인 확인 승인 서비스. 화면 13 에 대응한다.
 *
 * <p>INV-4 를 완성하는 자리다. 확정 서비스가 "승인되지 않은 개입이 있으면
 * 확정할 수 없다"를 강제한다면, 이 서비스는 "그 승인이 누구에 의해
 * 이루어질 수 있는가"를 강제한다. 둘이 갖추어져야 통제가 성립한다.
 *
 * <p><b>두 가지 검사가 핵심이다.</b>
 * <ul>
 *   <li><b>직무 분리</b> — 판정을 수행한 심사자는 자기 건을 승인할 수 없다.
 *       절차상 두 단계를 거쳤어도 같은 사람이라면 실질은 단독 확정이고,
 *       INV-4 가 금지하는 것이 바로 그것이다. 보조수단성 점검항목 ④에
 *       대응한다.</li>
 *   <li><b>승인 권한</b> — 규칙이 {@code approverRoles} 에 지정한 역할만
 *       승인할 수 있다. 역할을 코드에 고정하지 않는 이유는 발동 조건을
 *       데이터로 둔 것과 같다(D-5).</li>
 * </ul>
 *
 * <p><b>승인자와 시각을 함께 기록한다.</b> 최초 설계의 {@code interventions}
 * 테이블에는 승인 여부 불리언만 있었고, 구현하면서 승인이라는 상태 전이의
 * 행위자가 어디에도 남지 않는다는 것을 발견해 컬럼 세 개를 추가했다.
 * 직무 분리를 검사해 놓고 그 검사를 통과한 사람을 기록하지 않으면,
 * 사후에 "이 건은 누가 풀어줬는가"에 답할 수 없어 통제가 성립하지 않는다.
 *
 * <p><b>명세와의 차이</b> — OpenAPI 명세는 이 엔드포인트가 개입 하나를
 * 반환하도록 정의했다. 그러나 한 청구에 여러 규칙이 동시에 발동할 수 있고,
 * 시드의 청구가 실제로 {@code P-07} 과 {@code P-03} 두 규칙에 걸린다.
 * 승인은 청구 단위의 행위이므로 대기 중인 개입을 한 번에 처리하고 목록을
 * 반환한다. 명세가 청구당 복수인 확인이 하나라고 가정한 것으로 보이며,
 * 이 차이는 기술서의 미해결 항목에 남긴다.
 */
@Service
public class InterventionApprovalService {

    private final ClaimRepository claimRepository;
    private final InterventionRepository interventionRepository;
    private final ReviewRepository reviewRepository;
    private final RuleEvaluator ruleEvaluator;

    /**
     * 의존성을 주입받는다.
     *
     * @param claimRepository 청구 조회
     * @param interventionRepository 개입 이력 조회
     * @param reviewRepository 현재 판정 조회. 직무 분리 검사에 쓴다
     * @param ruleEvaluator 승인 권한 판별
     */
    public InterventionApprovalService(ClaimRepository claimRepository,
                                       InterventionRepository interventionRepository,
                                       ReviewRepository reviewRepository,
                                       RuleEvaluator ruleEvaluator) {
        this.claimRepository = claimRepository;
        this.interventionRepository = interventionRepository;
        this.reviewRepository = reviewRepository;
        this.ruleEvaluator = ruleEvaluator;
    }

    /**
     * 청구의 승인 대기 개입을 일괄 처리한다.
     *
     * @param claimId 대상 청구 식별자
     * @param request 승인 여부와 사유
     * @param actor 처리를 수행하는 사용자
     * @return 처리된 개입 목록
     * @throws ResourceNotFoundException 청구가 없거나 승인 대기 개입이 없는 경우
     * @throws InvariantViolationException 직무 분리 또는 승인 권한을 위반한 경우
     */
    @Transactional
    public List<InterventionResponse> resolveDualCheck(Long claimId, DualCheckRequest request, User actor) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("claim", claimId));

        List<Intervention> pending = interventionRepository.findAllByClaimId(claimId).stream()
                .filter(Intervention::blocksDecision)
                .toList();
        if (pending.isEmpty()) {
            throw new ResourceNotFoundException("pendingIntervention", claimId);
        }

        verifySegregationOfDuties(claim, actor);
        pending.forEach(intervention -> verifyApproverRole(intervention, actor));

        LocalDateTime now = LocalDateTime.now();
        pending.forEach(intervention ->
                intervention.resolve(request.approved(), actor, now, request.note()));

        return pending.stream().map(InterventionResponse::from).toList();
    }

    /**
     * 판정을 수행한 심사자가 자기 건을 승인하려는 것은 아닌지 확인한다.
     *
     * <p>현재 판정 중 하나라도 이 사용자가 내린 것이면 거부한다. 항목이
     * 여럿일 때 일부만 본인이 판정했더라도 마찬가지다. 승인은 청구 단위
     * 행위이므로 그 청구에 자기 판단이 섞여 있으면 독립적인 확인이 아니다.
     *
     * <p>청구의 배정 심사자를 보지 않고 실제 판정자를 보는 이유는, 배정이
     * 도중에 바뀌어도 이미 내려진 판정의 주체는 변하지 않기 때문이다.
     *
     * @param claim 대상 청구
     * @param actor 승인을 시도하는 사용자
     * @throws InvariantViolationException 본인이 판정한 건인 경우
     */
    private void verifySegregationOfDuties(Claim claim, User actor) {
        List<Review> currentReviews = reviewRepository.findCurrentByClaimId(claim.getId());
        boolean reviewedByActor = currentReviews.stream()
                .anyMatch(review -> review.getReviewer().getId().equals(actor.getId()));
        if (reviewedByActor) {
            throw new InvariantViolationException(
                    ErrorCode.SELF_APPROVAL_FORBIDDEN,
                    Map.of("claimNo", claim.getClaimNo(), "actorId", actor.getId()));
        }
    }

    /**
     * 규칙이 지정한 승인 권한을 가진 사용자인지 확인한다.
     *
     * <p>규칙 없이 만들어진 개입은 승인 역할을 판별할 근거가 없으므로
     * 심사관리자만 처리할 수 있는 것으로 본다. 현재 구현에서 승인 대기
     * 개입은 모두 규칙으로부터 생성되지만, 나중에 다른 경로가 생겨도
     * 권한이 열려 있는 상태로 남지 않게 한다.
     *
     * @param intervention 처리할 개입
     * @param actor 승인을 시도하는 사용자
     * @throws InvariantViolationException 승인 권한이 없는 경우
     */
    private void verifyApproverRole(Intervention intervention, User actor) {
        InterventionRule rule = intervention.getInterventionRule();
        boolean permitted = rule == null
                ? actor.isReviewManager()
                : ruleEvaluator.canApprove(rule, actor.getRole());
        if (!permitted) {
            throw new InvariantViolationException(
                    ErrorCode.APPROVER_ROLE_REQUIRED,
                    Map.of("actorRole", actor.getRole().name(),
                            "ruleCode", rule == null ? "-" : rule.getCode()));
        }
    }
}
