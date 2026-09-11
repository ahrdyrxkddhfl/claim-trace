package com.claimtrace.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.claimtrace.domain.User;
import com.claimtrace.dto.DecisionResult;
import com.claimtrace.dto.ExplanationResponse;
import com.claimtrace.service.ClaimDecisionService;
import com.claimtrace.service.ExplanationService;
import com.claimtrace.support.ActorResolver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 청구 단위 API.
 *
 * <p>확정은 요청 본문이 없다. 확정할 내용은 이미 항목별 판정으로 저장되어
 * 있고, 이 호출은 그것들을 확정 상태로 전이시키는 행위이기 때문이다.
 * 본문으로 판정 값을 받으면 항목별로 저장한 판정과 확정 시점의 값이
 * 어긋날 수 있는 경로가 생긴다.
 */
@RestController
@RequestMapping("/claims")
@Tag(name = "Reviewer", description = "심사자 · 사내 콘솔")
public class ClaimController {

    private final ClaimDecisionService claimDecisionService;
    private final ExplanationService explanationService;
    private final ActorResolver actorResolver;

    /**
     * 의존성을 주입받는다.
     *
     * @param claimDecisionService 판정 확정 서비스
     * @param explanationService 설명문 초안 생성 서비스
     * @param actorResolver 행위자 결정
     */
    public ClaimController(ClaimDecisionService claimDecisionService,
                           ExplanationService explanationService,
                           ActorResolver actorResolver) {
        this.claimDecisionService = claimDecisionService;
        this.explanationService = explanationService;
        this.actorResolver = actorResolver;
    }

    /**
     * 청구의 판정을 확정한다.
     *
     * @param claimId 확정할 청구 식별자
     * @param actorId 행위자 식별자. 인증을 대신하는 헤더다
     * @return 200 과 확정 결과
     */
    @PostMapping("/{claimId}/decision")
    @Operation(
            summary = "청구 판정 확정 (단일 진입점)",
            description = """
                    청구 전체의 판정을 확정하고 상태를 DECIDED 로 전이시킨다.

                    검증: INV-10 전 항목 판정 · INV-4 개입 승인 · INV-6 배정 확인

                    확정 이후 근거와 판정은 변경할 수 없다 (INV-7).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "확정 완료"),
            @ApiResponse(responseCode = "400", description = "미판정 항목 존재 (E-9)"),
            @ApiResponse(responseCode = "403", description = "복수인 확인 미승인 (E-4) · 타 심사자 배정 건 (E-5)"),
            @ApiResponse(responseCode = "404", description = "대상 청구 없음"),
            @ApiResponse(responseCode = "409", description = "이미 확정된 청구")
    })
    public ResponseEntity<DecisionResult> decide(
            @PathVariable Long claimId,
            @RequestHeader("X-Actor-Id") Long actorId) {

        User actor = actorResolver.resolve(actorId);
        return ResponseEntity.ok(claimDecisionService.decide(claimId, actor));
    }

    /**
     * 고객 설명문 초안을 생성한다.
     *
     * <p>이미 접수된 국소 설명서의 본문을 채운다. 설명서 신청은 고객 포털의
     * 책임이며 구현 범위 밖이므로, 접수된 설명서가 없으면 404 를 반환한다.
     *
     * @param claimId 대상 청구 식별자
     * @param actorId 행위자 식별자. 인증을 대신하는 헤더다
     * @return 201 과 본문이 채워진 설명서
     */
    @PostMapping("/{claimId}/explanations/draft")
    @Operation(
            summary = "고객 설명문 초안 생성 (단일 진입점)",
            description = """
                    disclosureLevel = CUSTOMER 인 채택 근거만으로 초안을 생성한다 (INV-9).

                    검증: INV-5 부지급·일부지급 항목의 고객용 부정 근거 · INV-6 배정 확인

                    초안은 심사자가 검토·확정해야 고객에게 발급된다. 완전 자동 발급 경로는 없다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "초안 생성 완료"),
            @ApiResponse(responseCode = "400", description = "고객용 부정 근거 부재 (E-3) · 미판정 항목 존재"),
            @ApiResponse(responseCode = "403", description = "타 심사자 배정 건 (E-5)"),
            @ApiResponse(responseCode = "404", description = "대상 청구 없음 · 접수된 설명서 없음")
    })
    public ResponseEntity<ExplanationResponse> draftExplanation(
            @PathVariable Long claimId,
            @RequestHeader("X-Actor-Id") Long actorId) {

        User actor = actorResolver.resolve(actorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(explanationService.draft(claimId, actor));
    }
}
