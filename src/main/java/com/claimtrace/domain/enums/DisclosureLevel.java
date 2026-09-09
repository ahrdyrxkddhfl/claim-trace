package com.claimtrace.domain.enums;

/**
 * 근거의 공개 수준.
 *
 * <p>G3 을 데이터로 표현한 값이다. 어떤 근거가 고객에게 공개 가능한지는
 * 근거 자체의 속성이지 응답을 만드는 시점의 판단이 아니라는 것이 D-4 의
 * 논지다. 애플리케이션 레이어에서 필터링하면 그 로직이 여러 엔드포인트에
 * 흩어져 누락이 생긴다.
 *
 * <p>INV-9 는 고객용 엔드포인트가 이 값으로 필터링할 것을 요구한다.
 * 예외 코드가 없는 불변조건인데, 위반이 오류 응답이 아니라 정보 유출로
 * 나타나기 때문이다. 서버 내부 규칙으로만 강제된다.
 *
 * <p>기본값은 출처에 따라 갈린다. {@code EvidenceSource.RULE} 은 CUSTOMER,
 * {@code EvidenceSource.AI} 는 INTERNAL 이다.
 */
public enum DisclosureLevel {

    /** 내부용. 심사자에게만 보이며 고객 설명문 생성에서 제외된다. */
    INTERNAL("내부용"),

    /** 고객용. 설명문 초안의 재료가 된다. {@code content_customer} 가 채워져야 한다. */
    CUSTOMER("고객용");

    private final String label;

    DisclosureLevel(String label) {
        this.label = label;
    }

    /**
     * 화면에 표시할 한글 명칭을 반환한다.
     *
     * @return 한글 라벨
     */
    public String getLabel() {
        return label;
    }
}
