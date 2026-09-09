package com.claimtrace.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.claimtrace.domain.User;
import com.claimtrace.dto.DecisionResult;
import com.claimtrace.service.ClaimDecisionService;
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
    private final ActorResolver actorResolver;

    /**
     * 의존성을 주입받는다.
     *
     * @param claimDecisionService 판정 확정 서비스
     * @param actorResolver 행위자 결정
     */
    public ClaimController(ClaimDecisionService claimDecisionService, ActorResolver actorResolver) {
        this.claimDecisionService = claimDecisionService;
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
}
