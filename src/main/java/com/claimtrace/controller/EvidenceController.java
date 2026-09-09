package com.claimtrace.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.claimtrace.domain.User;
import com.claimtrace.dto.EvidenceResponse;
import com.claimtrace.dto.EvidenceStatusRequest;
import com.claimtrace.service.EvidenceService;
import com.claimtrace.support.ActorResolver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 근거 카드 API.
 *
 * <p>두 엔드포인트의 기본 경로가 서로 달라 클래스 수준 매핑을 두지 않았다.
 * 근거 목록은 항목에 딸린 자원이라 {@code /claim-items} 아래에 있고, 상태
 * 변경은 근거 자체를 가리키므로 {@code /evidences} 아래에 있다. 경로를
 * 억지로 맞추면 URL 이 자원 구조를 잘못 나타내게 된다.
 */
@RestController
@Tag(name = "Reviewer", description = "심사자 · 사내 콘솔")
public class EvidenceController {

    private final EvidenceService evidenceService;
    private final ActorResolver actorResolver;

    /**
     * 의존성을 주입받는다.
     *
     * @param evidenceService 근거 검토 서비스
     * @param actorResolver 행위자 결정
     */
    public EvidenceController(EvidenceService evidenceService, ActorResolver actorResolver) {
        this.evidenceService = evidenceService;
        this.actorResolver = actorResolver;
    }

    /**
     * 근거를 채택하거나 기각한다.
     *
     * @param evidenceId 근거 식별자
     * @param actorId 행위자 식별자. 인증을 대신하는 헤더다
     * @param request 전이할 상태와 사유
     * @return 200 과 변경된 근거
     */
    @PutMapping("/evidences/{evidenceId}/status")
    @Operation(
            summary = "근거 채택·기각",
            description = """
                    근거의 상태를 전이시킨다. 기각된 근거는 삭제되지 않고 사유와 함께 보존된다 (D-3).

                    검증: INV-11 기각 사유 · INV-6 배정 확인 · INV-7 확정 여부
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "상태 변경 완료"),
            @ApiResponse(responseCode = "400", description = "기각 사유 미선택 (E-10)"),
            @ApiResponse(responseCode = "403", description = "타 심사자 배정 건 (E-5)"),
            @ApiResponse(responseCode = "404", description = "대상 근거 없음"),
            @ApiResponse(responseCode = "409", description = "이미 확정된 청구의 근거 (E-6)")
    })
    public ResponseEntity<EvidenceResponse> changeStatus(
            @PathVariable Long evidenceId,
            @RequestHeader("X-Actor-Id") Long actorId,
            @Valid @RequestBody EvidenceStatusRequest request) {

        User actor = actorResolver.resolve(actorId);
        return ResponseEntity.ok(evidenceService.changeStatus(evidenceId, request, actor));
    }

    /**
     * 항목의 근거 카드 목록을 조회한다.
     *
     * @param itemId 항목 식별자
     * @return 200 과 근거 목록
     */
    @GetMapping("/claim-items/{itemId}/evidences")
    @Operation(
            summary = "근거 카드 목록",
            description = "내부용 근거와 기각된 근거를 포함한 전체 근거를 반환한다. 심사자용 응답이다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "대상 항목 없음")
    })
    public ResponseEntity<List<EvidenceResponse>> findByItem(@PathVariable Long itemId) {
        return ResponseEntity.ok(evidenceService.findByItem(itemId));
    }
}
