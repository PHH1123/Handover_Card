package com.handovercard.auth.oauth2;

/** 소셜 로그인 흐름에서 우리 쪽 규칙 때문에 로그인을 끝내지 못한 경우. */
public class SocialLoginException extends RuntimeException {

    public SocialLoginException(String message) {
        super(message);
    }

    public SocialLoginException(String message, Throwable cause) {
        super(message, cause);
    }
}
