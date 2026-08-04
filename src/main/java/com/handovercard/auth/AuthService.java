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
import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    public AuthService(MemberRepository memberRepository, RefreshTokenRepository refreshTokenRepository,
                        PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                        JwtTokenProvider jwtTokenProvider, JwtProperties jwtProperties) {
        this.memberRepository = memberRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public MemberResponse signup(SignupRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("Email already registered: " + request.email());
        }
        Member member = new Member(request.email(), passwordEncoder.encode(request.password()),
                request.name(), MemberRole.USER);
        member = memberRepository.save(member);
        return MemberResponse.from(member);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Member member;
        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
            member = ((CustomUserDetails) authentication.getPrincipal()).getMember();
        } catch (AuthenticationException e) {
            throw new InvalidCredentialsException();
        }
        return issueTokens(member);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        Claims claims = parseRefreshClaims(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenId(claims.getId())
                .orElseThrow(() -> new InvalidTokenException("Unknown refresh token"));
        if (!stored.isValid()) {
            throw new InvalidTokenException("Refresh token expired or revoked");
        }
        stored.setRevoked(true);
        return issueTokens(stored.getMember());
    }

    @Transactional
    public void logout(RefreshRequest request) {
        try {
            Claims claims = parseRefreshClaims(request.refreshToken());
            refreshTokenRepository.findByTokenId(claims.getId())
                    .ifPresent(stored -> stored.setRevoked(true));
        } catch (InvalidTokenException ignored) {
            // logout is idempotent — an already-invalid token is effectively logged out
        }
    }

    private Claims parseRefreshClaims(String token) {
        Claims claims;
        try {
            claims = jwtTokenProvider.parseClaims(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Malformed or expired refresh token");
        }
        if (!jwtTokenProvider.isRefreshToken(claims)) {
            throw new InvalidTokenException("Not a refresh token");
        }
        return claims;
    }

    private TokenResponse issueTokens(Member member) {
        JwtTokenProvider.GeneratedToken access = jwtTokenProvider.generateAccessToken(member);
        JwtTokenProvider.GeneratedToken refresh = jwtTokenProvider.generateRefreshToken(member);
        refreshTokenRepository.save(new RefreshToken(member, refresh.tokenId(), refresh.expiresAt()));
        return new TokenResponse(access.token(), refresh.token(), "Bearer", jwtProperties.accessTokenValiditySeconds());
    }
}
