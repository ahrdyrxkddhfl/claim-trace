package com.claimtrace.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 개입 요구에 대한 처리 이력. 승인과 반려가 일어날 때마다 한 행씩 쌓인다.
 *
 * <p><b>요구 1건에 처리 N건이다.</b> {@link Intervention} 은 "규칙이 이
 * 청구에 복수인 확인을 요구했다"는 사실 하나를 뜻하고, 그 요구가 어떻게
 * 처리되었는지는 여러 번 일어날 수 있다. 반려되어 서류를 보완한 뒤 다시
 * 승인되는 흐름이 실제로 존재하므로, 처리를 요구와 같은 행에 담으면 나중
 * 처리가 앞 처리를 지운다.
 *
 * <p><b>이 엔티티가 없었을 때 무슨 일이 일어났는가.</b> 승인·반려가
 * {@code Intervention} 의 네 컬럼을 제자리에서 덮어썼다. 반려된 개입은
 * {@code approved = false} 라 {@link Intervention#blocksDecision()} 이 참으로
 * 남고, 그래서 같은 엔드포인트로 다시 승인할 수 있다. 그 순간 누가 왜
 * 반려했는지가 사라졌다. 판정은 대체되어도 이전 판정이 이력으로 남는데
 * (D-6) 통제 승인만 덮어쓰는 비대칭이었다. 보조수단성 ⑧이 요구하는 것은
 * 개입 사실의 기록이 아니라 그 내용의 기록이므로, 반려 사유가 남지 않으면
 * 통제가 성립하지 않는다.
 *
 * <p><b>추가만 한다.</b> 수정·삭제 메서드를 두지 않았다. 이력이 고쳐질 수
 * 있으면 그것은 이력이 아니다. 현재 상태는 {@link Intervention} 이 계속
 * 들고 있고 INV-4 의 검사 대상도 그대로이므로, 이 테이블은 판정을 바꾸지
 * 않고 기록만 보탠다.
 *
 * @see Intervention
 */
@Entity
@Table(
        name = "intervention_approvals",
        indexes = {
                @Index(name = "idx_intervention_approvals_intervention",
                        columnList = "intervention_id, id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterventionApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 처리 대상이 된 개입 요구. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intervention_id", nullable = false)
    private Intervention intervention;

    /** 이 처리가 승인이었는지 반려였는지. 반려도 처리이므로 기록 대상이다. */
    @Column(nullable = false)
    private boolean approved;

    /** 처리를 수행한 사용자. INV-1 이 요구하는 행위자다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approver_id", nullable = false)
    private User approver;

    /** 처리 사유. 반려 시 기재를 권장한다. */
    @Column(columnDefinition = "text")
    private String note;

    /**
     * 처리 시각.
     *
     * <p>{@code @CreationTimestamp} 를 쓰지 않고 인자로 받는다. 같은
     * 트랜잭션에서 {@link Intervention} 의 현재 상태에 찍히는 시각과 정확히
     * 같아야, 이력의 마지막 행과 현재 상태가 어긋나지 않는다.
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime approvedAt;

    private InterventionApproval(Intervention intervention, boolean approved,
                                 User approver, LocalDateTime approvedAt, String note) {
        this.intervention = intervention;
        this.approved = approved;
        this.approver = approver;
        this.approvedAt = approvedAt;
        this.note = note;
    }

    /**
     * 처리 이력 한 건을 만든다.
     *
     * @param intervention 처리 대상 개입
     * @param approved 승인이면 {@code true}, 반려이면 {@code false}
     * @param approver 처리를 수행한 사용자
     * @param approvedAt 처리 시각
     * @param note 처리 사유. 없으면 {@code null}
     * @return 저장 전의 이력 행
     */
    public static InterventionApproval of(Intervention intervention, boolean approved,
                                          User approver, LocalDateTime approvedAt, String note) {
        return new InterventionApproval(intervention, approved, approver, approvedAt, note);
    }
}
