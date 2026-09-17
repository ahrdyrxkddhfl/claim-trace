package com.claimtrace.invariant;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import com.claimtrace.domain.User;
import com.claimtrace.domain.enums.ItemDecision;
import com.claimtrace.dto.ReviewRequest;
import com.claimtrace.repository.ReviewRepository;
import com.claimtrace.service.ReviewService;
import com.claimtrace.support.ActorResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 동시 요청에서의 INV-12 검증.
 *
 * <p><b>이 테스트는 구현의 결함을 찾아내기 위해 작성되었다.</b> 판정 저장
 * 서비스는 "이전 현재 판정을 읽고 → 새 판정을 저장하고 → 이전 판정의 플래그를
 * 내린다"는 순서로 동작한다. 단일 요청에서는 항목당 현재 판정이 정확히 1건으로
 * 유지되지만, 같은 항목에 두 요청이 동시에 들어오면 이렇게 된다.
 *
 * <pre>
 * 요청 A: 이전 현재 판정 조회 → 없음
 * 요청 B: 이전 현재 판정 조회 → 없음      (A 가 아직 저장하지 않았다)
 * 요청 A: 새 판정 저장 (is_current = true)
 * 요청 B: 새 판정 저장 (is_current = true)  → 현재 판정 2건
 * </pre>
 *
 * <p>둘 다 물릴 이전 판정을 찾지 못해 플래그 이관이 일어나지 않는다. H2 는
 * 부분 UNIQUE 인덱스를 지원하지 않으므로 DB 도 이것을 막지 못한다. 결과적으로
 * 한 항목에 "현재 판정"이 두 건 남고, 확정 시 어느 쪽이 반영될지는 조회 순서에
 * 달리게 된다.
 *
 * <p>드문 상황이 아니다. 심사자가 브라우저 탭 두 개에서 같은 항목을 저장하거나,
 * 응답이 느려 저장 버튼을 두 번 누르면 그대로 재현된다.
 *
 * <p><b>{@code @Transactional} 을 붙이지 않는다.</b> 각 스레드가 자기 트랜잭션을
 * 가져야 경합이 재현된다. 테스트 트랜잭션 안에서 실행하면 모든 스레드가 같은
 * 트랜잭션을 공유하거나 서로를 기다려 경합 자체가 생기지 않는다. 대신
 * {@code @DirtiesContext} 로 이 클래스가 끝난 뒤 컨텍스트를 새로 만들어,
 * 여기서 쌓인 판정이 다른 테스트의 전제를 바꾸지 않게 한다.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("동시성 — INV-12")
class ConcurrencyInvariantTest {

    /** 시드의 심사자 김영희. 청구 1 의 배정 심사자다. */
    private static final long REVIEWER = 1L;

    /** 진찰료. AI 권고가 PAY 이므로 같은 판정을 보내면 오버라이드가 생기지 않는다. */
    private static final long ITEM_CONSULT = 1L;

    /** 동시에 보낼 요청 수. */
    private static final int CONCURRENT_REQUESTS = 8;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ActorResolver actorResolver;

    @Test
    @DisplayName("같은 항목에 동시 저장이 들어와도 현재 판정은 1건이다")
    void 동시_저장에도_현재_판정은_1건이다() throws Exception {
        User actor = actorResolver.resolve(REVIEWER);

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch fire = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONCURRENT_REQUESTS);

        List<Throwable> failures = new ArrayList<>();
        AtomicInteger succeeded = new AtomicInteger();

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            final int sequence = i;
            pool.submit(() -> {
                try {
                    // 모든 스레드가 준비될 때까지 기다렸다가 동시에 출발한다.
                    // 순차 실행이면 경합이 재현되지 않는다.
                    ready.countDown();
                    fire.await();
                    reviewService.save(ITEM_CONSULT, new ReviewRequest(
                            ItemDecision.PAY, 19200,
                            "동시 저장 검증 " + sequence, null, null), actor);
                    succeeded.incrementAndGet();
                } catch (Throwable t) {
                    synchronized (failures) {
                        failures.add(t);
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS), "스레드 준비가 끝나지 않았다");
        fire.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "동시 저장이 제한 시간 안에 끝나지 않았다");
        pool.shutdown();

        // 저장이 실패한 요청이 있다면 원인을 드러낸다. 방어 수단이 경합을
        // 직렬화하는 것이 목적이지, 요청을 떨어뜨리는 것이 목적은 아니다.
        if (!failures.isEmpty()) {
            throw new AssertionError(
                    "동시 저장 중 " + failures.size() + "건이 예외로 실패했다: " + failures.get(0), failures.get(0));
        }

        assertEquals(CONCURRENT_REQUESTS, succeeded.get(), "모든 요청이 저장되어야 한다");
        assertEquals(CONCURRENT_REQUESTS, reviewRepository.findHistoryByItemId(ITEM_CONSULT).size(),
                "판정은 이력으로 모두 남아야 한다 (D-6)");
        assertEquals(1, reviewRepository.countCurrentByItemId(ITEM_CONSULT),
                "동시 저장이 있어도 항목당 현재 판정은 정확히 1건이어야 한다 (INV-12)");
    }
}
