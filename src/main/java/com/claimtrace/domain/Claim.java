package com.claimtrace.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.claimtrace.domain.enums.ClaimStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 청구 건.
 *
 * <p>심사의 단위이며 항목({@link ClaimItem})을 여러 개 갖는다. 판정은 항목별로
 * 이루어지지만 확정은 청구 단위로 이루어진다. 이 비대칭이 INV-10 의 이유다.
 * 항목 하나라도 판정되지 않으면 청구를 확정할 수 없다.
 *
 * <p>{@code status} 가 두 불변조건의 판단 근거가 된다.
 * <ul>
 *   <li>INV-7 — {@link ClaimStatus#DECIDED} 인 청구의 근거는 변경할 수 없다.
 *       위반 시 409(E-6).</li>
 *   <li>중복 확정 — 이미 확정된 청구를 다시 확정하려 하면 409.</li>
 * </ul>
 *
 * <p>청구인과 계약을 둘 다 참조한다. 계약을 따라가면 청구인을 얻을 수 있어
 * 중복으로 보이지만, 화면 8 심사 큐와 INV-6 소유권 검사가 청구인을 직접
 * 비교하므로 조인을 한 단계 줄인다.
 *
 * <p>{@code assignee} 가 {@code null} 인 청구는 아직 배당되지 않았거나
 * 자동처리 대상이다. 배당되지 않은 청구에는 판정이 존재할 수 없다.
 */
@Entity
@Table(
        name = "claims",
        indexes = {
                @Index(name = "idx_claims_assignee_status", columnList = "assignee_id, status"),
                @Index(name = "idx_claims_customer_received", columnList = "customer_id, received_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 청구번호. CLM-YYYY-MMDD-NNNNN 형식이며 청구를 식별하는 자연키다. */
    @Column(nullable = false, unique = true, length = 30)
    private String claimNo;

    /** 청구인. INV-6 소유권 검사의 비교 대상이다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** 적용 계약. 담보 한도와 자기부담률의 출처다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    /** 진행 상태. INV-7 과 중복 확정 차단의 판단 근거다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimStatus status;

    /** 진료 의료기관명. */
    @Column(nullable = false, length = 100)
    private String hospitalName;

    /** KCD 상병코드. 예: M54.5 */
    @Column(nullable = false, length = 10)
    private String diagnosisCode;

    /** 진료 시작일. */
    @Column(nullable = false)
    private LocalDate treatedFrom;

    /** 진료 종료일. */
    @Column(nullable = false)
    private LocalDate treatedTo;

    /** 청구인이 화면 3 에서 남긴 추가 기재사항. */
    @Column(columnDefinition = "text")
    private String customerMemo;

    /** 배정된 심사자. 미배당이거나 자동처리 건이면 {@code null}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    /** 배정 시각. 미배당이면 {@code null}. */
    private LocalDateTime assignedAt;

    /** 접수 시각. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    /** 판정 확정 시각. 미확정이면 {@code null}. */
    private LocalDateTime decidedAt;

    /**
     * 청구를 생성한다.
     *
     * <p>상태와 배정 정보는 생성자에서 받지 않는다. 새 청구는 언제나
     * {@link ClaimStatus#RECEIVED} 이고 배정은 별도의 전이이기 때문이다.
     * 생성 시점에 임의의 상태를 넣을 수 있게 하면 상태 전이 규칙을 우회하는
     * 경로가 생긴다.
     *
     * @param claimNo 청구번호
     * @param customer 청구인
     * @param policy 적용 계약
     * @param hospitalName 진료 의료기관명
     * @param diagnosisCode KCD 상병코드
     * @param treatedFrom 진료 시작일
     * @param treatedTo 진료 종료일
     * @param customerMemo 청구인 추가 기재사항. 없으면 {@code null}
     */
    @Builder
    private Claim(String claimNo, Customer customer, Policy policy, String hospitalName,
                  String diagnosisCode, LocalDate treatedFrom, LocalDate treatedTo, String customerMemo) {
        this.claimNo = claimNo;
        this.customer = customer;
        this.policy = policy;
        this.status = ClaimStatus.RECEIVED;
        this.hospitalName = hospitalName;
        this.diagnosisCode = diagnosisCode;
        this.treatedFrom = treatedFrom;
        this.treatedTo = treatedTo;
        this.customerMemo = customerMemo;
    }

    /**
     * 청구를 심사자에게 배정하고 심사중 상태로 전이시킨다.
     *
     * <p>배정과 상태 전이를 한 메서드로 묶은 것은 두 변경이 항상 함께
     * 일어나기 때문이다. 나누어 두면 배정만 하고 상태를 바꾸지 않은
     * 중간 상태가 만들어질 수 있다.
     *
     * @param reviewer 배정할 심사자
     * @param at 배정 시각
     */
    public void assignTo(User reviewer, LocalDateTime at) {
        this.assignee = reviewer;
        this.assignedAt = at;
        this.status = ClaimStatus.UNDER_REVIEW;
    }

    /**
     * 청구를 확정 상태로 전이시킨다.
     *
     * <p>INV-4(개입 승인)와 INV-10(전 항목 판정) 검사는 이 메서드가 아니라
     * 판정 확정 서비스가 수행한다. 두 조건 모두 청구 하나만 보아서는 판단할
     * 수 없고 항목과 개입 기록을 함께 조회해야 하기 때문이다. 엔티티는
     * 자기 자신만으로 판단 가능한 조건, 즉 이미 확정되었는지만 막는다.
     *
     * @param at 확정 시각
     * @throws IllegalStateException 이미 확정되었거나 종결된 청구인 경우
     */
    public void decide(LocalDateTime at) {
        if (isLocked()) {
            throw new IllegalStateException(
                    "이미 확정·종결된 청구입니다. claimNo=" + this.claimNo + ", status=" + this.status);
        }
        this.status = ClaimStatus.DECIDED;
        this.decidedAt = at;
    }

    /**
     * 확정된 청구인지 판별한다.
     *
     * @return 상태가 {@link ClaimStatus#DECIDED} 이면 {@code true}
     */
    public boolean isDecided() {
        return this.status == ClaimStatus.DECIDED;
    }

    /**
     * 판정과 근거가 잠긴 청구인지 판별한다.
     *
     * <p>INV-7 검사에 쓴다. 근거 상태 변경과 판정 저장이 이 값을 확인하고,
     * 참이면 409 를 반환한다.
     *
     * <p>{@link ClaimStatus#DECIDED} 뿐 아니라 {@link ClaimStatus#CLOSED} 도
     * 포함한다. 종결은 확정 이후의 상태이므로, 확정된 청구를 잠그면서
     * 종결된 청구를 열어두면 지급까지 끝난 건을 되돌릴 수 있게 된다.
     * INV-7 의 문언은 "확정된 청구"지만 그 취지는 판정이 끝난 뒤의 변경을
     * 막는 것이고, 종결은 그 이후다.
     *
     * <p>{@link ClaimStatus#OBJECTION} 은 포함하지 않는다. 이의제기 재검토
     * 중에 근거를 다시 검토해야 하는지는 설계가 규정하지 않은 공백이라,
     * API 명세의 문언대로 확정과 종결만 막는다. 이 공백은 기술서 7.5 에
     * 남긴다.
     *
     * @return 상태가 확정 또는 종결이면 {@code true}
     */
    public boolean isLocked() {
        return this.status == ClaimStatus.DECIDED || this.status == ClaimStatus.CLOSED;
    }

    /**
     * 주어진 사용자가 이 청구의 배정 심사자인지 판별한다.
     *
     * <p>INV-6 소유권 검사에 쓴다. 미배당 청구는 어떤 심사자의 것도 아니므로
     * 언제나 거짓이다.
     *
     * @param user 검사할 사용자
     * @return 배정 심사자가 존재하고 그 사용자와 같으면 {@code true}
     */
    public boolean isAssignedTo(User user) {
        return this.assignee != null && user != null && this.assignee.getId().equals(user.getId());
    }
}
