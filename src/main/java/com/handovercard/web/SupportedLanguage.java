package com.handovercard.web;

/**
 * SSR 업로드 폼의 언어 선택 목록. 저장되는 값은 기존과 동일한 언어 코드 문자열이며,
 * API는 여전히 임의의 코드를 받을 수 있다 — 여기 목록은 화면 입력 편의를 위한 것.
 */
public enum SupportedLanguage {

    EN("en", "영어"),
    KO("ko", "한국어"),
    JA("ja", "일본어"),
    ZH("zh", "중국어"),
    ES("es", "스페인어"),
    VI("vi", "베트남어");

    private final String code;
    private final String label;

    SupportedLanguage(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
