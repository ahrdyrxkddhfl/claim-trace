package com.claimtrace.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 청구인.
 *
 * <p>보험금을 청구하는 개인이며 고객 포털(화면 1~6)의 사용자다. 사내 사용자를
 * 나타내는 {@link User} 와는 별도 테이블로 둔다. 두 주체는 로그인 경로와
 * 접근 가능한 자원이 완전히 다르고, 하나의 테이블에 역할 컬럼으로 섞으면
 * INV-6(액터는 자신에게 배정·귀속된 자원만 접근한다)의 소유권 검사가
 * 두 갈래로 나뉘어 복잡해진다.
 *
 * <p>{@code passwordHash} 는 DBML 원본을 그대로 옮긴 것이며, 인증은 구현
 * 범위에서 제외되어 실제로 사용되지 않는다. 시드 데이터에도 더미 값이 들어간다.
 *
 * <p>{@code birthYear} 만 두고 생년월일 전체를 두지 않은 것은, 화면 8 심사
 * 큐에서 동명이인을 구분하는 용도로만 쓰이기 때문이다. 심사 판단에 필요한
 * 최소 범위로 제한한다.
 */
@Entity
@Table(name = "customers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 포털 로그인 아이디. 계정을 식별하는 자연키다. */
    @Column(nullable = false, unique = true, length = 50)
    private String loginId;

    /** 비밀번호 해시. 인증이 구현 범위 밖이라 저장만 하고 검증하지 않는다. */
    @Column(nullable = false, length = 255)
    private String passwordHash;

    /** 청구인 성명. */
    @Column(nullable = false, length = 50)
    private String name;

    /** 출생 연도. 화면 8 에서 동명이인 구분에만 쓴다. */
    @Column(nullable = false)
    private Short birthYear;

    /** 연락처. */
    @Column(nullable = false, length = 20)
    private String phone;

    /** 계정 생성 시각. 영속 시점에 Hibernate 가 채운다. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 청구인을 생성한다.
     *
     * <p>setter 를 두지 않으므로 생성 시점에 모든 필수 값이 채워져야 한다.
     * 청구인 정보는 이 시스템에서 갱신되지 않는다. 계약 관리 시스템이
     * 원본을 갖고 있고 본 시스템은 심사에 필요한 범위만 참조한다.
     *
     * @param loginId 포털 로그인 아이디
     * @param passwordHash 비밀번호 해시
     * @param name 성명
     * @param birthYear 출생 연도
     * @param phone 연락처
     */
    @Builder
    private Customer(String loginId, String passwordHash, String name, Short birthYear, String phone) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.birthYear = birthYear;
        this.phone = phone;
    }
}
