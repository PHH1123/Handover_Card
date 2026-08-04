package com.handovercard.security;

import com.handovercard.member.Member;
import com.handovercard.member.MemberRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SecurityException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-must-be-at-least-32-bytes-long";

    private final JwtProperties props = new JwtProperties(SECRET, 1800, 1_209_600);
    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(props);

    private Member member(long id) {
        Member member = new Member("user@example.com", "hashed", "Alex", MemberRole.USER);
        setId(member, id);
        return member;
    }

    private void setId(Member member, long id) {
        try {
            Field field = Member.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(member, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void generatesAccessTokenWithExpectedClaims() {
        JwtTokenProvider.GeneratedToken generated = tokenProvider.generateAccessToken(member(42L));

        Claims claims = tokenProvider.parseClaims(generated.token());
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getId()).isEqualTo(generated.tokenId());
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
        assertThat(tokenProvider.isAccessToken(claims)).isTrue();
        assertThat(tokenProvider.isRefreshToken(claims)).isFalse();
    }

    @Test
    void generatesRefreshTokenWithExpectedClaims() {
        JwtTokenProvider.GeneratedToken generated = tokenProvider.generateRefreshToken(member(7L));

        Claims claims = tokenProvider.parseClaims(generated.token());
        assertThat(claims.getSubject()).isEqualTo("7");
        assertThat(claims.getId()).isEqualTo(generated.tokenId());
        assertThat(tokenProvider.isRefreshToken(claims)).isTrue();
        assertThat(tokenProvider.isAccessToken(claims)).isFalse();
    }

    @Test
    void eachGeneratedTokenGetsAUniqueTokenId() {
        Member member = member(1L);
        JwtTokenProvider.GeneratedToken first = tokenProvider.generateRefreshToken(member);
        JwtTokenProvider.GeneratedToken second = tokenProvider.generateRefreshToken(member);

        assertThat(first.tokenId()).isNotEqualTo(second.tokenId());
    }

    @Test
    void expiredTokenFailsToParse() {
        JwtProperties expiredProps = new JwtProperties(SECRET, -10, -10);
        JwtTokenProvider expiredProvider = new JwtTokenProvider(expiredProps);
        JwtTokenProvider.GeneratedToken generated = expiredProvider.generateAccessToken(member(1L));

        assertThatThrownBy(() -> tokenProvider.parseClaims(generated.token()))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithDifferentSecretFailsToParse() {
        // same byte length as SECRET so both resolve to the same HMAC algorithm (HS256)
        JwtProperties otherProps = new JwtProperties("unit-test-secret-must-be-at-least-32-bytes-wide", 1800, 1800);
        JwtTokenProvider otherProvider = new JwtTokenProvider(otherProps);
        JwtTokenProvider.GeneratedToken generated = otherProvider.generateAccessToken(member(1L));

        assertThatThrownBy(() -> tokenProvider.parseClaims(generated.token()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void generatedExpiryMatchesConfiguredValidity() {
        Instant before = Instant.now();
        JwtTokenProvider.GeneratedToken generated = tokenProvider.generateAccessToken(member(1L));
        Instant after = Instant.now();

        assertThat(generated.expiresAt())
                .isAfterOrEqualTo(before.plusSeconds(1800))
                .isBeforeOrEqualTo(after.plusSeconds(1800));
    }
}
