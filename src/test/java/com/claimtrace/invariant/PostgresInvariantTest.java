package com.claimtrace.invariant;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.claimtrace.domain.User;
import com.claimtrace.domain.enums.ItemDecision;
import com.claimtrace.dto.ReviewRequest;
import com.claimtrace.repository.ReviewRepository;
import com.claimtrace.service.ReviewService;
import com.claimtrace.support.ActorResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 실제 PostgreSQL 에서의 INV-12 검증.
 *
 * <p>나머지 테스트는 H2 인메모리에서 돈다. H2 는 부분 UNIQUE 인덱스를
 * 지원하지 않아 "항목당 현재 판정 1건"을 DB 제약으로 표현할 수 없고,
 * 따라서 애플리케이션이 지키는지만 확인할 수 있다. 이 클래스는 Testcontainers
 * 로 진짜 PostgreSQL 을 띄워, 같은 불변조건이 <b>DB 층에서도</b> 강제되는지를
 * 확인한다.
 *
 * <p>두 층을 함께 두는 이유는 서로 막는 것이 다르기 때문이다.
 * <ul>
 *   <li><b>애플리케이션 층</b>(항목 행 배타 잠금) — 동시 요청을 직렬화해
 *       요청이 실패하지 않게 한다. 사용자는 대기할 뿐 거부당하지 않는다.</li>
 *   <li><b>DB 층</b>(부분 UNIQUE 인덱스) — 잠금을 우회하는 경로가 생겨도
 *       잘못된 상태 자체가 저장되지 않게 한다. 배치 작업이나 운영자의 직접
 *       수정처럼 애플리케이션을 거치지 않는 쓰기가 여기에 해당한다.</li>
 * </ul>
 *
 * <p>애플리케이션 층만 두면 우회 경로에서 무너지고, DB 층만 두면 정상적인
 * 동시 요청이 오류로 떨어진다. 규제 대응 시스템에서 "기록이 조용히
 * 어긋나는 것"과 "심사자가 저장에 실패하는 것" 중 어느 쪽도 감수할 수 없다.
 *
 * <p>도커가 필요하다. 도커를 쓸 수 없는 환경에서는 이 클래스만 실패하고
 * 나머지 검증에는 영향이 없다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("postgres")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("PostgreSQL — INV-12 DB 제약")
class PostgresInvariantTest {

    /** 시드의 심사자 김영희. 청구 1 의 배정 심사자다. */
    private static final long REVIEWER = 1L;

    /** 진찰료. AI 권고가 PAY 다. */
    private static final long ITEM_CONSULT = 1L;

    /** 확정된 청구 2 의 항목. 시드에 현재 판정이 이미 1건 있다. */
    private static final long ITEM_DECIDED_CLAIM = 5L;

    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("claimtrace")
                    .withUsername("claimtrace")
                    .withPassword("claimtrace");

    /**
     * 컨테이너가 띄워진 뒤 확정된 접속 정보를 스프링에 주입한다.
     *
     * <p>포트가 매번 달라지므로 설정 파일에 적을 수 없다.
     *
     * @param registry 동적 프로퍼티 등록기
     */
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ActorResolver actorResolver;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("부분 UNIQUE 인덱스가 두 번째 현재 판정을 거부한다")
    void 부분_유니크_인덱스가_두_번째_현재_판정을_거부한다() {
        // 애플리케이션을 거치지 않고 DB 에 직접 넣는다. 서비스의 잠금과
        // 검증을 모두 건너뛰므로, 막는 주체는 DB 뿐이다.
        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> insertCurrentReviewDirectly(9001L, ITEM_DECIDED_CLAIM));

        assertTrue(thrown.getMessage().contains("uk_reviews_current"),
                "부분 UNIQUE 인덱스가 위반을 보고해야 한다. 실제 메시지: " + thrown.getMessage());
    }

    @Test
    @DisplayName("대체된 판정은 인덱스에 걸리지 않아 이력이 쌓인다")
    void 대체된_판정은_인덱스에_걸리지_않는다() {
        User actor = actorResolver.resolve(REVIEWER);

        reviewService.save(ITEM_CONSULT, new ReviewRequest(
                ItemDecision.PAY, 19200, "최초 판정", null, null), actor);
        reviewService.save(ITEM_CONSULT, new ReviewRequest(
                ItemDecision.PAY, 24000, "재검토 결과 전액 지급으로 정정한다.", null, null), actor);
        reviewService.save(ITEM_CONSULT, new ReviewRequest(
                ItemDecision.PAY, 21000, "금액을 다시 산정한다.", null, null), actor);

        // 부분 인덱스는 is_current = true 인 행들 사이에서만 유일성을 요구한다.
        // 대체된 판정은 조건을 벗어나므로 몇 건이든 남을 수 있다(D-6).
        assertEquals(3, reviewRepository.findHistoryByItemId(ITEM_CONSULT).size(),
                "판정 이력은 모두 보존되어야 한다");
        assertEquals(1, reviewRepository.countCurrentByItemId(ITEM_CONSULT),
                "현재 판정은 1건이어야 한다");
    }

    @Test
    @DisplayName("동시 저장이 실제 DBMS 에서도 직렬화된다")
    void 동시_저장이_실제_DBMS_에서도_직렬화된다() throws Exception {
        User actor = actorResolver.resolve(REVIEWER);
        int concurrency = 6;

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch fire = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(concurrency);
        List<Throwable> failures = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            final int sequence = i;
            pool.submit(() -> {
                try {
                    fire.await();
                    reviewService.save(ITEM_CONSULT, new ReviewRequest(
                            ItemDecision.PAY, 19200,
                            "동시 저장 검증 " + sequence, null, null), actor);
                } catch (Throwable t) {
                    synchronized (failures) {
                        failures.add(t);
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        fire.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "동시 저장이 제한 시간 안에 끝나지 않았다");
        pool.shutdown();

        // 잠금이 제대로 걸렸다면 어느 요청도 인덱스 위반으로 떨어지지 않는다.
        // 여기서 실패가 나온다면 애플리케이션 층 방어가 부족하다는 뜻이다.
        if (!failures.isEmpty()) {
            throw new AssertionError(
                    "동시 저장 중 " + failures.size() + "건이 실패했다: " + failures.get(0), failures.get(0));
        }

        assertEquals(1, reviewRepository.countCurrentByItemId(ITEM_CONSULT),
                "동시 저장 후에도 현재 판정은 1건이어야 한다 (INV-12)");
    }

    /**
     * 애플리케이션을 거치지 않고 현재 판정을 직접 INSERT 한다.
     *
     * @param id 사용할 식별자
     * @param claimItemId 대상 항목 식별자
     */
    private void insertCurrentReviewDirectly(Long id, Long claimItemId) {
        jdbcTemplate.update("""
                INSERT INTO reviews
                    (id, claim_item_id, reviewer_id, decision, paid_amount, reason, is_current, decided_at)
                VALUES (?, ?, ?, ?, ?, ?, TRUE, ?)
                """,
                id, claimItemId, REVIEWER, ItemDecision.PAY.name(), 1000,
                "DB 제약 검증용 직접 삽입", Timestamp.valueOf(LocalDateTime.now()));
    }
}
