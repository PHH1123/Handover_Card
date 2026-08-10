package com.handovercard.auth.oauth2;

import com.handovercard.member.AuthProvider;

/**
 * 공급자마다 다른 사용자 정보 응답을 우리가 필요한 값만 남겨 통일한 형태.
 *
 * @param provider      로그인에 쓴 소셜 공급자
 * @param providerId    공급자가 발급한 불변 식별자 (Google `sub`, GitHub `id`)
 * @param email         공급자가 알려준 이메일
 * @param name          표시 이름
 * @param emailVerified 공급자가 이메일 소유를 확인했는지. 기존 계정과 연동할 때 이 값이 열쇠가 된다.
 */
public record OAuth2UserProfile(AuthProvider provider, String providerId, String email, String name,
                                 boolean emailVerified) {
}
