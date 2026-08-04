package com.handovercard.auth;

import com.handovercard.auth.dto.LoginRequest;
import com.handovercard.auth.dto.MemberResponse;
import com.handovercard.auth.dto.RefreshRequest;
import com.handovercard.auth.dto.SignupRequest;
import com.handovercard.auth.dto.TokenResponse;
import com.handovercard.member.Member;
import com.handovercard.member.MemberRepository;
import com.handovercard.member.MemberRole;
import com.handovercard.security.CustomUserDetails;
import com.handovercard.security.JwtProperties;
import com.handovercard.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private MemberRepository memberRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private PasswordEncoder passwordEncoder;
    private AuthenticationManager authenticationManager;
    private JwtTokenProvider jwtTokenProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        authenticationManager = mock(AuthenticationManager.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        JwtProperties jwtProperties = new JwtProperties("secret", 1800, 1_209_600);
        authService = new AuthService(memberRepository, refreshTokenRepository, passwordEncoder,
                authenticationManager, jwtTokenProvider, jwtProperties);
    }

    private Member member(long id) {
        Member member = new Member("user@example.com", "hashed-pw", "Alex", MemberRole.USER);
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

    private void stubTokenGeneration(Member member, String accessToken, String refreshToken, String refreshJti) {
        when(jwtTokenProvider.generateAccessToken(member))
                .thenReturn(new JwtTokenProvider.GeneratedToken(accessToken, "access-jti", Instant.now().plusSeconds(1800)));
        when(jwtTokenProvider.generateRefreshToken(member))
                .thenReturn(new JwtTokenProvider.GeneratedToken(refreshToken, refreshJti, Instant.now().plusSeconds(1_209_600)));
    }

    @Test
    void signupSavesNewMemberWhenEmailNotTaken() {
        when(memberRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-pw");
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
            Member m = invocation.getArgument(0);
            setId(m, 1L);
            return m;
        });

        MemberResponse response = authService.signup(new SignupRequest("user@example.com", "password123", "Alex"));

        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.name()).isEqualTo("Alex");
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    void signupThrowsDuplicateEmailExceptionWhenEmailTaken() {
        when(memberRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(new SignupRequest("user@example.com", "password123", "Alex")))
                .isInstanceOf(DuplicateEmailException.class);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void loginReturnsTokensOnValidCredentials() {
        Member member = member(1L);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new CustomUserDetails(member));
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        stubTokenGeneration(member, "access-jwt", "refresh-jwt", "refresh-jti");

        TokenResponse response = authService.login(new LoginRequest("user@example.com", "password123"));

        assertThat(response.accessToken()).isEqualTo("access-jwt");
        assertThat(response.refreshToken()).isEqualTo("refresh-jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(1800);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void loginThrowsInvalidCredentialsExceptionOnBadCredentials() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refreshRotatesTokenOnValidRefreshToken() {
        Member member = member(1L);
        RefreshToken stored = new RefreshToken(member, "old-jti", Instant.now().plusSeconds(3600));
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("old-jti");
        when(jwtTokenProvider.parseClaims("old-refresh-jwt")).thenReturn(claims);
        when(jwtTokenProvider.isRefreshToken(claims)).thenReturn(true);
        when(refreshTokenRepository.findByTokenId("old-jti")).thenReturn(Optional.of(stored));
        stubTokenGeneration(member, "new-access-jwt", "new-refresh-jwt", "new-jti");

        TokenResponse response = authService.refresh(new RefreshRequest("old-refresh-jwt"));

        assertThat(response.accessToken()).isEqualTo("new-access-jwt");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-jwt");
        assertThat(stored.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void refreshThrowsInvalidTokenExceptionWhenTokenUnknown() {
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("unknown-jti");
        when(jwtTokenProvider.parseClaims(anyString())).thenReturn(claims);
        when(jwtTokenProvider.isRefreshToken(claims)).thenReturn(true);
        when(refreshTokenRepository.findByTokenId("unknown-jti")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("some-jwt")))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refreshThrowsInvalidTokenExceptionWhenTokenRevoked() {
        Member member = member(1L);
        RefreshToken revoked = new RefreshToken(member, "revoked-jti", Instant.now().plusSeconds(3600));
        revoked.setRevoked(true);
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("revoked-jti");
        when(jwtTokenProvider.parseClaims(anyString())).thenReturn(claims);
        when(jwtTokenProvider.isRefreshToken(claims)).thenReturn(true);
        when(refreshTokenRepository.findByTokenId("revoked-jti")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("some-jwt")))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refreshThrowsInvalidTokenExceptionWhenTokenExpired() {
        Member member = member(1L);
        RefreshToken expired = new RefreshToken(member, "expired-jti", Instant.now().minusSeconds(10));
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("expired-jti");
        when(jwtTokenProvider.parseClaims(anyString())).thenReturn(claims);
        when(jwtTokenProvider.isRefreshToken(claims)).thenReturn(true);
        when(refreshTokenRepository.findByTokenId("expired-jti")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("some-jwt")))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refreshThrowsInvalidTokenExceptionWhenNotARefreshToken() {
        Claims claims = mock(Claims.class);
        when(jwtTokenProvider.parseClaims(anyString())).thenReturn(claims);
        when(jwtTokenProvider.isRefreshToken(claims)).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("access-jwt-used-as-refresh")))
                .isInstanceOf(InvalidTokenException.class);
        verify(refreshTokenRepository, never()).findByTokenId(anyString());
    }

    @Test
    void refreshThrowsInvalidTokenExceptionWhenParsingFails() {
        when(jwtTokenProvider.parseClaims(anyString())).thenThrow(new MalformedJwtException("bad"));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("garbage")))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void logoutRevokesStoredTokenWhenValid() {
        Member member = member(1L);
        RefreshToken stored = new RefreshToken(member, "jti-1", Instant.now().plusSeconds(3600));
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("jti-1");
        when(jwtTokenProvider.parseClaims("refresh-jwt")).thenReturn(claims);
        when(jwtTokenProvider.isRefreshToken(claims)).thenReturn(true);
        when(refreshTokenRepository.findByTokenId("jti-1")).thenReturn(Optional.of(stored));

        authService.logout(new RefreshRequest("refresh-jwt"));

        assertThat(stored.isRevoked()).isTrue();
    }

    @Test
    void logoutIsIdempotentWhenTokenAlreadyInvalid() {
        when(jwtTokenProvider.parseClaims(anyString())).thenThrow(new MalformedJwtException("bad"));

        authService.logout(new RefreshRequest("garbage"));
    }
}
