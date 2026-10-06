package com.claimtrace.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.claimtrace.domain.AiRecommendation;
import com.claimtrace.domain.Claim;
import com.claimtrace.domain.ClaimItem;
import com.claimtrace.domain.Evidence;
import com.claimtrace.dto.RecommendationRequest;
import com.claimtrace.dto.RecommendationResponse;
import com.claimtrace.exception.ErrorCode;
import com.claimtrace.exception.InvariantViolationException;
import com.claimtrace.exception.ResourceNotFoundException;
import com.claimtrace.repository.AiRecommendationRepository;
import com.claimtrace.repository.ClaimItemRepository;
import com.claimtrace.repository.ClaimRepository;
import com.claimtrace.repository.EvidenceRepository;

/**
 * AI 권고 수신 서비스.
 *
 * <p>모델이 보낸 권고를 저장하고, 기여도마다 AI 근거를 만든다. 같은 항목의
 * 이전 권고는 지우지 않고 최신 표시만 내린다. 판정이 어느 권고를 보고
 * 내려졌는지 나중에 확인할 수 있어야 하기 때문이다.
 *
 * <p><b>판정·개입·청구 상태에는 손대지 않는다(INV-13).</b> AI 는 액터가
 * 아니다(기술서 3.1). 새 권고로 발동할 개입 규칙이 생겨도 여기서 개입을
 * 기록하지 않는다. 그 평가는 사람이 판정을 저장할 때 일어난다.
 */
@Service
public class RecommendationService {

    private final ClaimRepository claimRepository;
    private final ClaimItemRepository claimItemRepository;
    private final AiRecommendationRepository aiRecommendationRepository;
    private final EvidenceRepository evidenceRepository;

    /**
     * 의존성을 주입받는다.
     *
     * @param claimRepository 청구 조회
     * @param claimItemRepository 청구의 항목 조회
     * @param aiRecommendationRepository 권고 조회·저장
     * @param evidenceRepository AI 근거 저장
     */
    public RecommendationService(ClaimRepository claimRepository,
                                 ClaimItemRepository claimItemRepository,
                                 AiRecommendationRepository aiRecommendationRepository,
                                 EvidenceRepository evidenceRepository) {
        this.claimRepository = claimRepository;
        this.claimItemRepository = claimItemRepository;
        this.aiRecommendationRepository = aiRecommendationRepository;
        this.evidenceRepository = evidenceRepository;
    }

    /**
     * 청구의 항목별 권고를 등록한다.
     *
     * <p>모든 검사를 저장보다 먼저 끝낸다. 항목 하나가 잘못되었는데 앞 항목의
     * 권고만 바뀌면, 모델이 보낸 한 번의 추론 결과가 절반만 반영된다.
     *
     * @param claimId 대상 청구 식별자
     * @param request 모델이 보낸 권고
     * @return 항목별 등록 결과
     * @throws ResourceNotFoundException 청구가 없거나 이 청구에 속하지 않은 항목이 있는 경우
     * @throws InvariantViolationException 확정된 청구이거나(INV-7) 같은 항목이 두 번 들어온 경우
     */
    @Transactional
    public List<RecommendationResponse> submit(Long claimId, RecommendationRequest request) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("claim", claimId));

        // INV-7 — 확정·종결된 청구의 근거는 바뀌지 않는다. 권고와 함께 AI 근거가
        // 생기므로 확정 뒤의 권고는 받지 않는다.
        if (claim.isLocked()) {
            throw new InvariantViolationException(
                    ErrorCode.CLAIM_ALREADY_DECIDED,
                    Map.of("claimNo", claim.getClaimNo(), "status", claim.getStatus().name()));
        }

        Map<Long, ClaimItem> items = claimItemRepository.findAllByClaimIdWithCoverage(claimId).stream()
                .collect(Collectors.toMap(ClaimItem::getId, Function.identity()));

        Set<Long> seen = new HashSet<>();
        for (RecommendationRequest.Item requested : request.items()) {
            if (!items.containsKey(requested.claimItemId())) {
                throw new ResourceNotFoundException("claimItem", requested.claimItemId());
            }
            // 한 요청 안에서 같은 항목에 권고가 둘이면 어느 쪽이 최신인지 정할 수 없다.
            if (!seen.add(requested.claimItemId())) {
                throw new InvariantViolationException(
                        ErrorCode.INVALID_REQUEST,
                        Map.of("detail", "같은 항목의 권고가 두 번 들어왔습니다",
                                "claimItemId", requested.claimItemId()));
            }
        }

        List<RecommendationResponse> results = new ArrayList<>();
        for (RecommendationRequest.Item requested : request.items()) {
            ClaimItem item = items.get(requested.claimItemId());
            aiRecommendationRepository.findLatestByItemId(item.getId())
                    .ifPresent(AiRecommendation::supersede);

            AiRecommendation saved = aiRecommendationRepository.save(AiRecommendation.builder()
                    .claimItem(item)
                    .modelName(request.modelName())
                    .modelVersion(request.modelVersion())
                    .exclusionProbability(requested.exclusionProbability())
                    .recommendation(requested.recommendation())
                    .threshold(request.threshold())
                    .build());

            for (RecommendationRequest.Contribution contribution : requested.contributions()) {
                evidenceRepository.save(Evidence.fromAi(
                        item, saved, contribution.polarity(),
                        contribution.feature() + ". 기여도 " + contribution.value().toPlainString() + ".",
                        contribution.value(), null));
            }

            results.add(new RecommendationResponse(
                    item.getId(), saved.getId(), saved.getRecommendation(),
                    saved.getExclusionProbability(), requested.contributions().size()));
        }
        return results;
    }
}
