package com.claimtrace.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.claimtrace.domain.User;
import com.claimtrace.dto.DualCheckRequest;
import com.claimtrace.dto.InterventionResponse;
import com.claimtrace.service.InterventionApprovalService;
import com.claimtrace.support.ActorResolver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 심사관리자 API.
 *
 * <p>경로를 {@code /admin} 아래로 분리한 것은 접근 주체가 다르기 때문이다.
 * 심사자용 엔드포인트와 섞으면 나중에 인증을 붙일 때 경로 단위로 권한을
 * 나눌 수 없다. 현재는 인증이 구현 범위 밖이라 실제 차단이 없고, 역할
 * 검사는 서비스가 개별적으로 수행한다.
 */
@RestController
@RequestMapping("/admin")
@Tag(name = "Admin", description = "심사관리자 · 개입 승인")
public class AdminController {

    private final InterventionApprovalService interventionApprovalService;
    private final ActorResolver actorResolver;

    /**
     * 의존성을 주입받는다.
     *
     * @param interventionApprovalService 복수인 확인 승인 서비스
     * @param actorResolver 행위자 결정
     */
    public AdminController(InterventionApprovalService interventionApprovalService,
                           ActorResolver actorResolver) {
        this.interventionApprovalService = interventionApprovalService;
        this.actorResolver = actorResolver;
    }

    /**
     * 청구의 복수인 확인을 승인하거나 반려한다.
     *
     * @param claimId 대상 청구 식별자
     * @param actorId 행위자 식별자. 인증을 대신하는 헤더다
     * @param request 승인 여부와 사유
     * @return 200 과 처리된 개입 목록
     */
    @PutMapping("/claims/{claimId}/dual-check")
    @Operation(
            summary = "복수인 확인 승인·반려",
            description = """
                    승인이 완료되어야 해당 청구를 확정할 수 있다 (INV-4).

                    검증: 직무 분리(판정자 본인 승인 불가) · 승인 권한(정책의 approverRoles)

                    한 청구에 여러 정책이 발동할 수 있으므로 대기 중인 개입을 일괄 처리하고 목록을 반환한다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 완료"),
            @ApiResponse(responseCode = "400", description = "승인 여부 미기재"),
            @ApiResponse(responseCode = "403", description = "본인이 판정한 건 · 승인 권한 없음"),
            @ApiResponse(responseCode = "404", description = "대상 청구 없음 · 승인 대기 개입 없음")
    })
    public ResponseEntity<List<InterventionResponse>> resolveDualCheck(
            @PathVariable Long claimId,
            @RequestHeader("X-Actor-Id") Long actorId,
            @Valid @RequestBody DualCheckRequest request) {

        User actor = actorResolver.resolve(actorId);
        return ResponseEntity.ok(
                interventionApprovalService.resolveDualCheck(claimId, request, actor));
    }
}
