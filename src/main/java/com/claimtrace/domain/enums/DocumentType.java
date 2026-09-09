package com.claimtrace.domain.enums;

/**
 * 청구인이 제출한 서류의 종류.
 *
 * <p>{@code RECEIPT} 와 {@code DETAIL} 이 OCR 대상이며, 여기서 추출된
 * 항목이 {@code claim_items} 로 생성된다(D-8). 진료비 영수증의 항목은
 * 급여(본인부담·공단부담·전액본인부담)와 비급여(선택진료료·선택진료료외)로
 * 정형화되어 있어 자동 추출이 가능하다.
 *
 * <p>항목 생성은 판정이 아니라 심사 대상 목록의 구성이므로 INV-13
 * ("외부시스템 입력은 어떤 판정도 확정하지 않는다")과 충돌하지 않는다.
 * 생성된 항목이 사람의 확인 없이 판정으로 이어지지 않도록 INV-10 이
 * 확정 경로를 막는다.
 */
public enum DocumentType {

    /** 진료비계산서·영수증. OCR 대상이며 항목 생성의 주 입력이다. */
    RECEIPT("진료비계산서·영수증"),

    /** 진료비세부내역서. OCR 대상이며 행위 코드와 회차를 보완한다. */
    DETAIL("진료비세부내역서"),

    /** 진료확인서. 진료 사실과 기간을 확인한다. */
    CONFIRMATION("진료확인서"),

    /** 의사 소견서. 상병과 치료 필요성에 대한 의학적 판단이 담긴다. */
    OPINION("의사 소견서"),

    /** 기타. 위 분류에 들어가지 않는 제출 서류. */
    OTHER("기타");

    private final String label;

    DocumentType(String label) {
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
