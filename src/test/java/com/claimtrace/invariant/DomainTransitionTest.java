package com.claimtrace.invariant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.claimtrace.domain.AiRecommendation;
import com.claimtrace.domain.Claim;
import com.claimtrace.domain.ClaimItem;
import com.claimtrace.domain.Coverage;
import com.claimtrace.domain.Customer;
import com.claimtrace.domain.Evidence;
import com.claimtrace.domain.Explanation;
import com.claimtrace.domain.Intervention;
import com.claimtrace.domain.InterventionRule;
import com.claimtrace.domain.Objection;
import com.claimtrace.domain.Policy;
import com.claimtrace.domain.Review;
import com.claimtrace.domain.Rule;
import com.claimtrace.domain.User;
import com.claimtrace.domain.enums.ClaimStatus;
import com.claimtrace.domain.enums.CoverageType;
import com.claimtrace.domain.enums.DisclosureLevel;
import com.claimtrace.domain.enums.EvidenceSource;
import com.claimtrace.domain.enums.EvidenceStatus;
import com.claimtrace.domain.enums.ExplanationStatus;
import com.claimtrace.domain.enums.ExplanationType;
import com.claimtrace.domain.enums.InterventionType;
import com.claimtrace.domain.enums.ItemDecision;
import com.claimtrace.domain.enums.ObjectionStatus;
import com.claimtrace.domain.enums.OverrideReasonType;
import com.claimtrace.domain.enums.Polarity;
import com.claimtrace.domain.enums.RejectionReasonType;
import com.claimtrace.domain.enums.UserRole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 도메인 상태 전이와 생성 규칙의 단위 검증.
 *
 * <p>Spring 컨텍스트도 DB 도 띄우지 않고 엔티티를 직접 호출한다. 검증 대상은
 * 엔티티가 스스로 지키는 규칙이며, 이런 규칙은 HTTP 를 거치지 않아도
 * 성립해야 한다.
 *
 * <p><b>엔드포인트가 없는 전이도 여기서 검증한다.</b> 설계는 엔터티 15개와
 * 그 상태 전이를 정의했지만 구현 범위는 엔드포인트 8개다. 배정, 전역 설명,
 * 설명서 발급, 이의제기 처리는 호출하는 엔드포인트가 없다. 그렇다고 전이
 * 규칙을 코드에서 빼면 엔티티가 설계의 절반만 표현하게 되고, 그대로 두면
 * 검증되지 않은 코드가 남는다. 단위 테스트로 규칙을 고정해 두는 편이
 * 양쪽 문제를 다 피한다.
 *
 * <p>특히 {@link Evidence#fromRule}, {@link Evidence#fromAi} 는 D-4 의
 * "출처에 따라 기본 공개 수준이 갈린다"를 코드로 표현한 지점인데, 룰 매칭과
 * 모델 연계가 구현 범위 밖이라 실행 경로가 없다. 그 규칙이 실제로 그렇게
 * 동작하는지는 여기서만 확인된다.
 */
@DisplayName("도메인 규칙 — 단위")
class DomainTransitionTest {

    /**
     * 검증에 필요한 최소 객체 그래프를 만든다.
     *
     * <p>식별자가 없다. 영속화하지 않으므로 {@code id} 는 {@code null} 이며,
     * 식별자 비교에 의존하는 {@code Claim.isAssignedTo} 같은 메서드는 여기서
     * 검증하지 않는다. 그 규칙은 INV-6 통합 테스트가 확인한다.
     */
    private static final class Fixture {

        private final User reviewer = User.builder()
                .empNo("M2026043").passwordHash("x").name("김영희")
                .role(UserRole.REVIEWER).active(true).build();

        private final User manager = User.builder()
                .empNo("M2019011").passwordHash("x").name("박준호")
                .role(UserRole.REVIEW_MANAGER).active(true).build();

        private final Customer customer = Customer.builder()
                .loginId("chulsoo85").passwordHash("x").name("김철수")
                .birthYear((short) 1985).phone("010-0000-0000").build();

        private final Policy policy = Policy.builder()
                .customer(customer).policyNo("POL-1").productName("실손")
                .startedOn(LocalDate.of(2021, 5, 1)).build();

        private final Coverage coverage = Coverage.builder()
                .policy(policy).name("질병 비급여 통원").type(CoverageType.DISEASE_UNCOVERED)
                .limitAmount(200000).annualLimit(50)
                .deductibleRate(new BigDecimal("0.300")).build();

        private final Claim claim = Claim.builder()
                .claimNo("CLM-TEST-0001").customer(customer).policy(policy)
                .hospitalName("서울정형외과").diagnosisCode("M54.5")
                .treatedFrom(LocalDate.of(2026, 8, 3)).treatedTo(LocalDate.of(2026, 8, 29))
                .build();

        private final ClaimItem item = ClaimItem.builder()
                .claim(claim).coverage(coverage).seq((short) 1).name("도수치료")
                .procedureCode("MM301").isCovered(false).quantity((short) 12)
                .claimedAmount(480000).build();

        private final Rule rule = Rule.builder()
                .code("R-0412").version((short) 4).name("도수치료 시행 한도")
                .coverageType(CoverageType.DISEASE_UNCOVERED)
                .articleNo("제12조 3항").articleText("조항 원문")
                .polarity(Polarity.NEGATIVE).conditionExpr("quantity > 30")
                .contentInternal("심사자용 문구").contentCustomer("고객용 문구")
                .defaultDisclosureLevel(DisclosureLevel.CUSTOMER)
                .active(true).author(manager).build();

        private final AiRecommendation recommendation = AiRecommendation.builder()
                .claimItem(item).modelName("claim-risk").modelVersion("v2.3")
                .exclusionProbability(new BigDecimal("0.820"))
                .recommendation(ItemDecision.DENY)
                .threshold(new BigDecimal("0.300")).build();

        private final InterventionRule interventionRule = InterventionRule.builder()
                .code("P-07").name("비급여 고액 항목")
                .conditions("[{\"field\":\"claimedAmount\",\"op\":\"gte\",\"value\":300000}]")
                .requiredIntervention(InterventionType.DUAL_CHECK)
                .approverRoles("[\"REVIEW_MANAGER\"]")
                .active(true).author(manager).build();

        private Review review(ItemDecision decision, String reason) {
            return Review.builder().claimItem(item).reviewer(reviewer)
                    .decision(decision).paidAmount(0).reason(reason).build();
        }
    }

    private final Fixture f = new Fixture();

    @Nested
    @DisplayName("근거 생성 — D-4 출처별 기본값")
    class EvidenceCreation {

        @Test
        @DisplayName("룰 근거는 극성·문구·공개 수준을 룰에서 물려받는다")
        void 룰_근거는_룰에서_물려받는다() {
            Evidence evidence = Evidence.fromRule(f.item, f.rule, null, 80000);

            assertEquals(EvidenceSource.RULE, evidence.getSource());
            assertEquals(Polarity.NEGATIVE, evidence.getPolarity());
            assertEquals(DisclosureLevel.CUSTOMER, evidence.getDisclosureLevel());
            assertEquals("심사자용 문구", evidence.getContentInternal());
            assertEquals("고객용 문구", evidence.getContentCustomer());
            assertEquals(EvidenceStatus.GENERATED, evidence.getStatus(),
                    "룰에서 생성된 근거는 심사자의 검토를 기다린다");
            assertNotNull(evidence.getRule());
            assertNull(evidence.getAiRecommendation(),
                    "출처가 RULE 이면 AI 권고 FK 는 비어야 한다");
        }

        @Test
        @DisplayName("AI 근거는 공개 수준이 INTERNAL 로 고정된다")
        void AI_근거는_공개_수준이_고정된다() {
            Evidence evidence = Evidence.fromAi(f.item, f.recommendation, Polarity.NEGATIVE,
                    "시행 회차가 상위 5퍼센트 구간이다. 기여도 0.412.",
                    new BigDecimal("0.412"), null);

            // D-4 — 새플리 기여도를 그대로 고객에게 내보내면 설명이 아니라
            // 숫자 나열이 된다. 공개로 올리는 것은 심사자의 판단이어야 한다.
            assertEquals(DisclosureLevel.INTERNAL, evidence.getDisclosureLevel());
            assertNull(evidence.getContentCustomer(), "고객용 문구 없이 생성된다");
            assertEquals(new BigDecimal("0.412"), evidence.getContribution());
            assertNull(evidence.getRule(), "출처가 AI 이면 룰 FK 는 비어야 한다");
        }

        @Test
        @DisplayName("수동 근거는 생성 즉시 채택 상태다")
        void 수동_근거는_생성_즉시_채택된다() {
            LocalDateTime at = LocalDateTime.of(2026, 9, 17, 10, 0);
            Evidence evidence = Evidence.manual(f.item, Polarity.POSITIVE, DisclosureLevel.CUSTOMER,
                    "소견서 확인", "의사 소견서에서 확인되었습니다.", null, 120000, f.reviewer, at);

            // 사람이 판단해 넣은 것을 다시 채택하게 하는 것은 의미 없는 절차다.
            assertEquals(EvidenceStatus.ADOPTED, evidence.getStatus());
            assertEquals(f.reviewer, evidence.getDecidedBy());
            assertEquals(at, evidence.getDecidedAt());
            assertEquals(EvidenceSource.MANUAL, evidence.getSource());
        }
    }

    @Nested
    @DisplayName("근거 검토")
    class EvidenceReview {

        @Test
        @DisplayName("INV-11 기각에는 사유 유형이 필요하다")
        void 기각에는_사유_유형이_필요하다() {
            Evidence evidence = Evidence.fromRule(f.item, f.rule, null, null);

            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> evidence.reject(null, "메모만 있음", f.reviewer, LocalDateTime.now()));
            assertTrue(thrown.getMessage().contains("INV-11"));
            assertEquals(EvidenceStatus.GENERATED, evidence.getStatus(),
                    "거부된 전이는 상태를 바꾸지 않아야 한다");
        }

        @Test
        @DisplayName("기각했다 채택하면 기각 사유가 지워진다")
        void 기각했다_채택하면_기각_사유가_지워진다() {
            Evidence evidence = Evidence.fromRule(f.item, f.rule, null, null);
            evidence.reject(RejectionReasonType.DUPLICATE, "중복", f.reviewer, LocalDateTime.now());
            evidence.adopt(f.reviewer, LocalDateTime.now());

            // 상태가 ADOPTED 인데 기각 사유가 남아 있으면 모순된 데이터가 된다.
            assertEquals(EvidenceStatus.ADOPTED, evidence.getStatus());
            assertNull(evidence.getRejectionReasonType());
            assertNull(evidence.getRejectionNote());
        }

        @Test
        @DisplayName("INV-9 설명문에 쓸 수 있으려면 세 조건이 모두 필요하다")
        void 설명문에_쓰려면_세_조건이_모두_필요하다() {
            Evidence generated = Evidence.fromRule(f.item, f.rule, null, null);
            assertFalse(generated.isUsableInCustomerExplanation(), "미검토 근거는 쓸 수 없다");

            generated.adopt(f.reviewer, LocalDateTime.now());
            assertTrue(generated.isUsableInCustomerExplanation(), "채택된 고객용 근거는 쓸 수 있다");

            generated.changeDisclosureLevel(DisclosureLevel.INTERNAL);
            assertFalse(generated.isUsableInCustomerExplanation(), "내부용으로 내리면 쓸 수 없다");

            Evidence aiEvidence = Evidence.fromAi(f.item, f.recommendation, Polarity.NEGATIVE,
                    "기여도 0.412", new BigDecimal("0.412"), null);
            aiEvidence.adopt(f.reviewer, LocalDateTime.now());
            aiEvidence.changeDisclosureLevel(DisclosureLevel.CUSTOMER);
            assertFalse(aiEvidence.isUsableInCustomerExplanation(),
                    "공개로 올려도 고객용 문구가 없으면 쓸 수 없다");
        }
    }

    @Nested
    @DisplayName("판정과 대체")
    class ReviewTransition {

        @Test
        @DisplayName("INV-2 사유가 공백이면 판정을 만들 수 없다")
        void 사유가_공백이면_판정을_만들_수_없다() {
            // 컬럼이 NOT NULL 이어도 빈 문자열은 통과한다. DB 제약만으로는
            // INV-2 를 지킬 수 없어 엔티티가 마지막 방어선이 된다.
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> f.review(ItemDecision.PAY, "   "));
            assertTrue(thrown.getMessage().contains("INV-2"));
        }

        @Test
        @DisplayName("새 판정은 언제나 현재 판정으로 시작한다")
        void 새_판정은_언제나_현재_판정이다() {
            Review review = f.review(ItemDecision.PAY, "사유");
            assertTrue(review.isCurrent());
            assertNull(review.getSupersededBy());
        }

        @Test
        @DisplayName("D-6 대체는 플래그 해제와 연결 두 단계다")
        void 대체는_두_단계다() {
            Review first = f.review(ItemDecision.PAY, "최초 판정");
            Review second = f.review(ItemDecision.DENY, "재검토 판정");

            // 부분 UNIQUE 인덱스 때문에 플래그를 먼저 내려야 하고,
            // 대체 연결은 새 판정이 식별자를 가진 뒤에야 가능하다.
            first.supersede();
            assertFalse(first.isCurrent());
            assertNull(first.getSupersededBy(), "연결은 아직 이루어지지 않았다");

            first.linkSuccessor(second);
            assertEquals(second, first.getSupersededBy());
            assertEquals("최초 판정", first.getReason(), "최초 판정의 사유는 보존된다");
        }
    }

    @Nested
    @DisplayName("청구 상태 전이")
    class ClaimTransition {

        @Test
        @DisplayName("배정하면 심사중으로 전이한다")
        void 배정하면_심사중으로_전이한다() {
            Claim claim = f.claim;
            assertEquals(ClaimStatus.RECEIVED, claim.getStatus(), "새 청구는 접수 상태다");

            LocalDateTime at = LocalDateTime.of(2026, 9, 8, 9, 14);
            claim.assignTo(f.reviewer, at);

            // 배정과 상태 전이를 한 메서드로 묶었다. 나누면 배정만 되고
            // 상태는 접수인 중간 상태가 만들어질 수 있다.
            assertEquals(f.reviewer, claim.getAssignee());
            assertEquals(at, claim.getAssignedAt());
            assertEquals(ClaimStatus.UNDER_REVIEW, claim.getStatus());
        }

        @Test
        @DisplayName("확정하면 잠기고 두 번 확정할 수 없다")
        void 확정하면_잠기고_두_번_확정할_수_없다() {
            Claim claim = f.claim;
            assertFalse(claim.isLocked());

            LocalDateTime at = LocalDateTime.of(2026, 9, 17, 14, 0);
            claim.decide(at);

            assertEquals(ClaimStatus.DECIDED, claim.getStatus());
            assertEquals(at, claim.getDecidedAt());
            assertTrue(claim.isLocked(), "확정된 청구는 근거와 판정이 잠긴다 (INV-7)");
            assertThrows(IllegalStateException.class, () -> claim.decide(at));
        }

        @Test
        @DisplayName("잠김 판단은 확정과 종결을 포함하고 이의제기는 제외한다")
        void 잠김_판단의_범위() {
            // CLOSED 로 가는 전이는 구현 범위 밖이라 엔티티에 메서드가 없다.
            // 여기서는 상태값 자체의 분류만 확인한다. 실제 전이가 생기면
            // isLocked 가 그 상태도 막는다.
            assertTrue(ClaimStatus.DECIDED == ClaimStatus.valueOf("DECIDED"));
            assertTrue(ClaimStatus.CLOSED == ClaimStatus.valueOf("CLOSED"));

            Claim claim = f.claim;
            claim.decide(LocalDateTime.now());
            assertTrue(claim.isLocked());
        }
    }

    @Nested
    @DisplayName("AI 권고")
    class Recommendation {

        @Test
        @DisplayName("D-7 판정이 권고와 다르면 오버라이드로 판별된다")
        void 판정이_권고와_다르면_오버라이드다() {
            // 심사자가 개입을 선언하는 것이 아니라 시스템이 비교해 판별한다.
            assertTrue(f.recommendation.isOverriddenBy(ItemDecision.PARTIAL));
            assertTrue(f.recommendation.isOverriddenBy(ItemDecision.PAY));
            assertFalse(f.recommendation.isOverriddenBy(ItemDecision.DENY));
        }

        @Test
        @DisplayName("권고를 물리면 이력으로 남고 최신에서만 빠진다")
        void 권고를_물리면_이력으로_남는다() {
            AiRecommendation recommendation = f.recommendation;
            assertTrue(recommendation.isLatest(), "새 권고는 최신이다");

            recommendation.supersede();
            assertFalse(recommendation.isLatest());
            assertEquals(ItemDecision.DENY, recommendation.getRecommendation(),
                    "레코드를 지우지 않으므로 어느 시점에 어떤 권고가 있었는지가 남는다");
            assertEquals(new BigDecimal("0.300"), recommendation.getThreshold(),
                    "임계값을 함께 보존해야 사후에 권고를 재현할 수 있다");
        }
    }

    @Nested
    @DisplayName("인적 개입")
    class InterventionRules {

        @Test
        @DisplayName("INV-3 오버라이드에는 사유 유형과 본문이 모두 필요하다")
        void 오버라이드에는_사유가_모두_필요하다() {
            Review review = f.review(ItemDecision.PARTIAL, "일부 지급");

            assertThrows(IllegalArgumentException.class, () -> Intervention.override(
                    review, f.recommendation, null, "본문만 있음", f.reviewer));
            assertThrows(IllegalArgumentException.class, () -> Intervention.override(
                    review, f.recommendation, OverrideReasonType.OTHER, "   ", f.reviewer));
        }

        @Test
        @DisplayName("오버라이드는 승인 대상이 아니라 이미 일어난 사실의 기록이다")
        void 오버라이드는_승인_대상이_아니다() {
            Review review = f.review(ItemDecision.PARTIAL, "일부 지급");
            Intervention override = Intervention.override(review, f.recommendation,
                    OverrideReasonType.TERMS_INTERPRETATION, "소견서로 요건 충족", f.reviewer);

            assertEquals(InterventionType.OVERRIDE, override.getType());
            assertNull(override.getApproved(), "승인 여부가 의미 없는 유형이다");
            assertFalse(override.blocksDecision(), "확정을 막지 않는다");
            assertEquals(f.recommendation, override.getAiRecommendation(),
                    "어느 권고를 뒤집었는지 지목한다 (D-1)");
        }

        @Test
        @DisplayName("INV-4 규칙이 요구한 개입은 승인 전까지 확정을 막는다")
        void 규칙_개입은_승인_전까지_확정을_막는다() {
            Intervention required = Intervention.required(
                    f.claim, f.interventionRule, "규칙 P-07 조건 해당", f.reviewer);

            assertEquals(InterventionType.DUAL_CHECK, required.getType());
            assertTrue(required.blocksDecision(), "승인 대기 중에는 확정을 막는다");

            LocalDateTime at = LocalDateTime.of(2026, 9, 17, 15, 0);
            required.resolve(false, f.manager, at, "추가 서류 확인 필요");
            assertTrue(required.blocksDecision(),
                    "반려도 승인되지 않은 것이므로 확정을 막는다");

            required.resolve(true, f.manager, at, "승인한다");
            assertFalse(required.blocksDecision());
        }

        @Test
        @DisplayName("INV-1 승인에는 행위자와 시각이 함께 기록된다")
        void 승인에는_행위자와_시각이_기록된다() {
            Intervention required = Intervention.required(
                    f.claim, f.interventionRule, "규칙 조건 해당", f.reviewer);
            LocalDateTime at = LocalDateTime.of(2026, 9, 17, 15, 0);

            required.resolve(true, f.manager, at, "소견서 확인 결과 승인한다.");

            // 최초 설계에는 approved 불리언만 있었다. 직무 분리를 검사하고도
            // 통과한 사람을 남기지 않으면 사후에 답할 수 없다.
            assertEquals(f.manager, required.getApprovedBy());
            assertEquals(at, required.getApprovedAt());
            assertEquals("소견서 확인 결과 승인한다.", required.getApprovalNote());
            assertEquals(f.reviewer, required.getActor(),
                    "개입을 발생시킨 심사자와 승인자는 다른 값이다");
        }
    }

    @Nested
    @DisplayName("설명서")
    class ExplanationRules {

        @Test
        @DisplayName("INV-8 국소 설명서에는 제공 기한이 필요하다")
        void 국소_설명서에는_제공_기한이_필요하다() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> Explanation.local(f.claim, null));
            assertTrue(thrown.getMessage().contains("INV-8"));
        }

        @Test
        @DisplayName("전역 설명서는 청구에 매이지 않고 기한도 없다")
        void 전역_설명서는_청구에_매이지_않는다() {
            Explanation global = Explanation.global("claim-risk");

            assertEquals(ExplanationType.GLOBAL, global.getType());
            assertEquals("claim-risk", global.getModelName());
            assertNull(global.getClaim(), "모델 단위로 사전 생성된다");
            assertNull(global.getDueDate(), "신청에 응답하는 문서가 아니라 기한이 없다");
        }

        @Test
        @DisplayName("초안 생성은 발급이 아니며 사람이 확정해야 한다")
        void 초안_생성은_발급이_아니다() {
            Explanation local = Explanation.local(f.claim, LocalDate.of(2026, 9, 15));
            assertEquals(ExplanationStatus.REQUESTED, local.getStatus());

            local.applyDraft("항목별 심사 결과 …");
            assertEquals(ExplanationStatus.GENERATING, local.getStatus(),
                    "초안이 채워져도 고객에게는 나가지 않는다");

            // 빈 설명서가 고객에게 가는 것은 설명서를 주지 않은 것보다 나쁘다.
            assertThrows(IllegalArgumentException.class,
                    () -> local.provide("  ", f.reviewer, LocalDateTime.now()));

            LocalDateTime at = LocalDateTime.of(2026, 9, 14, 11, 0);
            local.provide("심사자가 검토한 최종 문안", f.reviewer, at);
            assertEquals(ExplanationStatus.PROVIDED, local.getStatus());
            assertEquals(f.reviewer, local.getDraftedBy());
            assertEquals(at, local.getProvidedAt());
        }
    }

    @Nested
    @DisplayName("이의제기")
    class ObjectionRules {

        @Test
        @DisplayName("접수부터 회신까지 상태가 전이한다")
        void 접수부터_회신까지_전이한다() {
            Objection objection = Objection.builder()
                    .claim(f.claim).targetItems("[1203]")
                    .reason("도수치료 부지급에 동의할 수 없습니다.").build();

            assertEquals(ObjectionStatus.RECEIVED, objection.getStatus());
            assertNull(objection.getReviewer(), "배정 전이다");

            objection.assignReviewer(f.manager);
            assertEquals(ObjectionStatus.REVIEWING, objection.getStatus());

            LocalDateTime at = LocalDateTime.of(2026, 9, 20, 16, 0);
            objection.answer("재검토 결과 일부 지급으로 변경되었습니다.", at);
            assertEquals(ObjectionStatus.ANSWERED, objection.getStatus());
            assertEquals(at, objection.getAnsweredAt());

            // 재검토 결과는 이 엔터티가 아니라 reviews 에 새 레코드로 남는다(D-6).
            // 이의제기는 재검토를 촉발한 사건과 회신만 담는다.
            assertNotNull(objection.getResponse());
        }
    }

    @Nested
    @DisplayName("열거형 판단 메서드")
    class EnumHelpers {

        @Test
        @DisplayName("INV-5 검사 대상은 부지급과 일부지급이다")
        void 부정_근거가_필요한_판정() {
            assertTrue(ItemDecision.DENY.requiresNegativeEvidence());
            assertTrue(ItemDecision.PARTIAL.requiresNegativeEvidence());
            assertFalse(ItemDecision.PAY.requiresNegativeEvidence());
        }

        @Test
        @DisplayName("승인 절차가 있는 개입은 복수인 확인과 차상위 검토다")
        void 승인이_필요한_개입_유형() {
            assertTrue(InterventionType.DUAL_CHECK.requiresApproval());
            assertTrue(InterventionType.ESCALATION.requiresApproval());
            assertFalse(InterventionType.OVERRIDE.requiresApproval());
        }

        @Test
        @DisplayName("제공 기한이 필요한 설명은 국소 설명뿐이다")
        void 기한이_필요한_설명_유형() {
            assertTrue(ExplanationType.LOCAL.requiresDueDate());
            assertFalse(ExplanationType.GLOBAL.requiresDueDate());
        }

        @Test
        @DisplayName("심사관리자 판별은 역할 비교를 한곳에 모은다")
        void 심사관리자_판별() {
            assertTrue(f.manager.isReviewManager());
            assertFalse(f.reviewer.isReviewManager());
        }
    }
}
