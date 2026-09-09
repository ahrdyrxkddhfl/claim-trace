package com.claimtrace.domain.enums;

/**
 * 근거를 기각한 사유의 유형.
 *
 * <p>{@code EvidenceStatus.REJECTED} 로 전이할 때 필수다(INV-11).
 * 상세 사유({@code rejection_note})는 선택이지만 유형은 반드시 고른다.
 * 자유 텍스트만 받으면 집계가 불가능하기 때문이다.
 *
 * <p>이 유형별 집계가 화면 13 하단과 화면 14 의 룰별 기각률로 이어지고,
 * 기술서 1.7 ③이 말하는 "축적된 기록이 룰·모델 개선의 입력이 된다"의
 * 실제 경로가 된다. {@code RULE_MISAPPLIED} 비중이 높은 룰은 조건식을
 * 손봐야 하고, {@code DOCUMENT_MISMATCH} 비중이 높으면 OCR 정확도를
 * 의심할 근거가 된다.
 */
public enum RejectionReasonType {

    /** 룰 오적용. 조건식이 이 사안에 맞지 않게 매칭되었다. */
    RULE_MISAPPLIED("룰 오적용"),

    /** 서류 확인 결과 상이. 실제 제출 서류의 내용이 근거와 다르다. */
    DOCUMENT_MISMATCH("서류 확인 결과 상이"),

    /** 약관 해석 상이. 조항 자체는 맞으나 해석이 다르다. */
    TERMS_INTERPRETATION("약관 해석 상이"),

    /** 중복 근거. 다른 근거와 같은 내용이다. */
    DUPLICATE("중복 근거"),

    /** 기타. 위 유형에 들어가지 않으며 상세 사유 기재를 권장한다. */
    OTHER("기타");

    private final String label;

    RejectionReasonType(String label) {
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
