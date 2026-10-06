package com.claimtrace.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.claimtrace.dto.RecommendationRequest;
import com.claimtrace.dto.RecommendationResponse;
import com.claimtrace.service.RecommendationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 외부시스템 API.
 *
 * <p>행위자 헤더를 받지 않는다. AI 모델은 액터가 아니라 데이터 공급원이고
 * (기술서 3.1), 이 경로가 만드는 권고와 AI 근거에는 사람 행위자가 없다.
 * 모델과 시스템 사이의 인증은 사내 인증과 함께 구현 범위 밖이다.
 */
@RestController
@RequestMapping("/external")
@Tag(name = "External", description = "외부시스템 · AI 모델")
public class ExternalController {

    private final RecommendationService recommendationService;

    /**
     * 의존성을 주입받는다.
     *
     * @param recommendationService 권고 수신 서비스
     */
    public ExternalController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    /**
     * AI 모델의 권고와 기여도를 받는다.
     *
     * @param claimId 대상 청구 식별자
     * @param request 항목별 권고와 기여도
     * @return 201 과 항목별 등록 결과
     */
    @PostMapping("/claims/{claimId}/recommendations")
    @Operation(
            summary = "AI 모델 권고 및 기여도 전달",
            description = """
                    항목별 보상제외 확률·권고·기여도를 받아 권고와 AI 근거(내부용·미검토)를 만든다.
                    같은 항목의 이전 권고는 지우지 않고 최신 표시만 내린다.

                    판정·개입·청구 상태에는 접근하지 않는다 (INV-13). 확정된 청구에는 보낼 수 없다 (INV-7).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "권고 등록 완료"),
            @ApiResponse(responseCode = "400", description = "요청 형식 오류 · 같은 항목 중복"),
            @ApiResponse(responseCode = "404", description = "청구 없음 · 이 청구에 속하지 않은 항목"),
            @ApiResponse(responseCode = "409", description = "이미 확정된 청구 (E-6)")
    })
    public ResponseEntity<List<RecommendationResponse>> submitRecommendations(
            @PathVariable Long claimId,
            @Valid @RequestBody RecommendationRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recommendationService.submit(claimId, request));
    }
}
