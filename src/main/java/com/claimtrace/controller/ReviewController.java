package com.claimtrace.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.claimtrace.domain.User;
import com.claimtrace.dto.ReviewRequest;
import com.claimtrace.dto.ReviewResponse;
import com.claimtrace.service.ReviewService;
import com.claimtrace.support.ActorResolver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;

/**
 * 항목 판정 API.
 *
 * <p>컨트롤러는 세 가지만 한다. 요청을 받고, 행위자를 결정하고, 서비스를
 * 부른다. 불변조건 검증은 전부 서비스에 있다. 같은 규칙을 컨트롤러마다
 * 되풀이하면 어느 한 곳이 빠졌을 때 우회 경로가 생기기 때문이다.
 *
 * <p>예외를 잡지 않는 것도 같은 이유다. 모든 오류 응답은
 * {@code GlobalExceptionHandler} 한 곳에서 만들어져야 형태가 일정하다.
 *
 * <p>{@code X-Actor-Id} 헤더는 인증을 대신하는 임시 수단이다. 실제
 * 시스템이라면 인증된 세션에서 행위자를 꺼낸다. 자세한 사정은
 * {@link ActorResolver} 에 적었다.
 */
@RestController
@RequestMapping("/claim-items")
@Tag(name = "Reviewer", description = "심사자 · 사내 콘솔")
public class ReviewController {

    private final ReviewService reviewService;
    private final ActorResolver actorResolver;

    /**
     * 의존성을 주입받는다.
     *
     * @param reviewService 판정 저장 서비스
     * @param actorResolver 행위자 결정
     */
    public ReviewController(ReviewService reviewService, ActorResolver actorResolver) {
        this.reviewService = reviewService;
        this.actorResolver = actorResolver;
    }

    /**
     * 항목 판정을 저장한다.
     *
     * <p>호출마다 새 판정 레코드가 쌓이고 현재 판정 플래그가 이관된다.
     * AI 권고와 다른 판정이면 같은 트랜잭션에서 개입 기록이 생성되며,
     * 응답의 {@code intervention} 에 그 결과가 실린다.
     *
     * @param itemId 판정할 항목 식별자
     * @param actorId 행위자 식별자. 인증을 대신하는 헤더다
     * @param request 판정 내용
     * @return 201 과 저장된 판정
     */
    @PostMapping("/{itemId}/reviews")
    @Operation(
            summary = "항목 판정 저장 (단일 진입점)",
            description = """
                    판정을 저장하고, AI 권고와 다르면 개입 기록을 같은 트랜잭션에서 생성한다.

                    검증: INV-2 판정 사유 · INV-3 오버라이드 사유 · INV-6 배정 확인 · INV-7 확정 여부 · INV-12 현재 판정 이관
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "판정 저장 완료"),
            @ApiResponse(responseCode = "400", description = "판정 사유 미기재 (E-1) · 오버라이드 사유 미기재 (E-2)"),
            @ApiResponse(responseCode = "403", description = "타 심사자 배정 건 (E-5)"),
            @ApiResponse(responseCode = "409", description = "이미 확정된 청구 (E-6)")
    })
    
    public ResponseEntity<ReviewResponse> save(
            @PathVariable Long itemId,
            @RequestHeader("X-Actor-Id") Long actorId,
            @Valid @RequestBody ReviewRequest request) {

        User actor = actorResolver.resolve(actorId);
        ReviewResponse response = reviewService.save(itemId, request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 항목의 판정 이력을 조회한다.
     *
     * <p>대체된 판정도 모두 포함한다. {@code isCurrent} 가 참인 레코드는
     * 정확히 1건이어야 하며, 이 응답으로 그것을 눈으로 확인할 수 있다.
     *
     * @param itemId 항목 식별자
     * @return 판정 시각 내림차순의 판정 목록>
     */
    @GetMapping("/{itemId}/reviews")
    @Operation(
            summary = "항목 판정 이력",
            description = "재검토 이력을 포함한 전체 판정을 시간 역순으로 반환한다. isCurrent 가 참인 레코드는 정확히 1건이다 (INV-12).")
    public ResponseEntity<List<ReviewResponse>> history(@PathVariable Long itemId) {
        return ResponseEntity.ok(reviewService.findHistory(itemId));
    }
}
