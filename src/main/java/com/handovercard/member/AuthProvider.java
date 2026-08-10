package com.handovercard.member;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** 회원이 인증에 쓰는 수단. {@code LOCAL}은 이메일/비밀번호, 나머지는 OAuth 2.0 공급자다. */
public enum AuthProvider {

    LOCAL("이메일"),
    GOOGLE("Google"),
    GITHUB("GitHub");

    private final String displayName;

    AuthProvider(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Spring Security {@code ClientRegistration}의 등록 ID. */
    public String getRegistrationId() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 등록 ID(`google`, `github`)를 공급자로 바꾼다. 소셜 로그인에 쓸 수 없는 값이면 비어 있는 결과. */
    public static Optional<AuthProvider> ofRegistrationId(String registrationId) {
        if (registrationId == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(provider -> provider != LOCAL)
                .filter(provider -> provider.getRegistrationId().equalsIgnoreCase(registrationId))
                .findFirst();
    }
}
