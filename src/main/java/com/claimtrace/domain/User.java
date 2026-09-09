package com.claimtrace.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.claimtrace.domain.enums.UserRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사내 사용자. 심사자와 심사관리자.
 *
 * <p>INV-1("모든 상태 전이에는 행위자가 있다")이 가리키는 행위자가 이 엔티티다.
 * {@code reviews.reviewer_id} 와 {@code interventions.actor_id} 가 NOT NULL 로
 * 이 테이블을 참조하며, 그래서 판정과 개입은 주체 없이 존재할 수 없다.
 *
 * <p>테이블명이 {@code users} 인 것은 단수형 {@code user} 가 H2 와 PostgreSQL
 * 양쪽에서 예약어이기 때문이다. 클래스명은 {@code User} 로 두고 테이블명만
 * 복수형으로 매핑한다.
 *
 * <p>{@code active} 를 두고 삭제 대신 비활성화를 쓴다. 퇴사한 심사자의
 * 계정을 지우면 그 사람이 남긴 판정 이력이 행위자를 잃는다. 설계 원칙 3
 * (기록은 지우지 않는다)이 사용자에도 적용된다.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사번. 사내에서 사람을 식별하는 자연키다. */
    @Column(nullable = false, unique = true, length = 20)
    private String empNo;

    /** 비밀번호 해시. 인증이 구현 범위 밖이라 저장만 하고 검증하지 않는다. */
    @Column(nullable = false, length = 255)
    private String passwordHash;

    /** 성명. 화면 9 판정 이력과 화면 13 개입 이력에 표시된다. */
    @Column(nullable = false, length = 50)
    private String name;

    /** 역할. 로그인 후 진입 화면과 접근 가능한 자원을 가른다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    /** 재직 여부. 비활성 사용자는 새 배정을 받지 않으나 과거 이력은 유지된다. */
    @Column(nullable = false)
    private boolean active;

    /** 계정 생성 시각. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 사내 사용자를 생성한다.
     *
     * @param empNo 사번
     * @param passwordHash 비밀번호 해시
     * @param name 성명
     * @param role 역할
     * @param active 재직 여부
     */
    @Builder
    private User(String empNo, String passwordHash, String name, UserRole role, boolean active) {
        this.empNo = empNo;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
        this.active = active;
    }

    /**
     * 심사관리자인지 판별한다.
     *
     * <p>복수인 확인 승인 권한을 검사할 때 쓴다. 역할 값을 서비스 여러 곳에서
     * 직접 비교하면 조건이 흩어지므로 이 메서드로 모은다.
     *
     * @return 역할이 {@link UserRole#REVIEW_MANAGER} 이면 {@code true}
     */
    public boolean isReviewManager() {
        return this.role == UserRole.REVIEW_MANAGER;
    }
}
