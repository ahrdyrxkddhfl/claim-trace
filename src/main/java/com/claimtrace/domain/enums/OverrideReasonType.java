package com.claimtrace.domain.enums;

/**
 * AI 권고를 뒤집은 사유의 유형.
 *
 * <p>{@code RejectionReasonType} 과 값이 일부 겹치지만 대상이 다르다.
 * 이쪽은 모델 권고 전체를 뒤집은 이유이고, 저쪽은 개별 근거 하나를
 * 배제한 이유다. 두 Enum 을 합치면 "모델이 틀렸다"는 판단과 "이 근거가
 * 잘못됐다"는 판단이 같은 집계에 섞인다.
 *
 * <p>{@code MODEL_FALSE_POSITIVE} 가 이쪽에만 있는 것이 그 차이를 보여준다.
 * 심사자가 모델의 오탐을 지목한 기록이며, 이 유형의 누적이 모델 재학습
 * 신호가 된다.
 *
 * <p>판정이 AI 권고와 다를 때 필수다(INV-3). 미입력 시 400(E-2).
 */
public enum OverrideReasonType {

    /** 서류 확인 결과 상이. 모델이 참조한 값과 실제 서류가 다르다. */
    DOCUMENT_MISMATCH("서류 확인 결과 상이"),

    /** 약관 해석 상이. 모델이 적용한 조항 해석에 동의하지 않는다. */
    TERMS_INTERPRETATION("약관 해석 상이"),

    /** 모델 오탐 판단. 모델의 보상제외 확률이 사안에 비해 과다하다고 본다. */
    MODEL_FALSE_POSITIVE("모델 오탐 판단"),

    /** 기타. 위 유형에 들어가지 않으며 상세 사유 기재를 권장한다. */
    OTHER("기타");

    private final String label;

    OverrideReasonType(String label) {
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
