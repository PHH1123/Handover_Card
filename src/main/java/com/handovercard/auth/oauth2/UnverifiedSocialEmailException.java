package com.handovercard.auth.oauth2;

/**
 * 공급자가 이메일을 주지 않았거나 소유를 확인해 주지 않은 경우.
 *
 * <p>확인되지 않은 이메일을 받아들이면, 남의 이메일 주소를 적어 둔 소셜 계정으로 그 사람의 기존 회원
 * 계정에 그대로 로그인할 수 있게 된다. 이메일이 계정을 잇는 유일한 열쇠이므로 여기서 막는다.
 */
public class UnverifiedSocialEmailException extends SocialLoginException {

    public UnverifiedSocialEmailException(String message) {
        super(message);
    }
}
