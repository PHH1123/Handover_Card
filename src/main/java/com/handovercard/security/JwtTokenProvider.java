package com.handovercard.security;

import com.handovercard.member.Member;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final JwtProperties props;

    public JwtTokenProvider(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    public GeneratedToken generateAccessToken(Member member) {
        Instant expiresAt = Instant.now().plusSeconds(props.accessTokenValiditySeconds());
        String tokenId = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .subject(member.getId().toString())
                .id(tokenId)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .claim(CLAIM_EMAIL, member.getEmail())
                .claim(CLAIM_ROLE, member.getRole().name())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new GeneratedToken(token, tokenId, expiresAt);
    }

    public GeneratedToken generateRefreshToken(Member member) {
        Instant expiresAt = Instant.now().plusSeconds(props.refreshTokenValiditySeconds());
        String tokenId = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .subject(member.getId().toString())
                .id(tokenId)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new GeneratedToken(token, tokenId, expiresAt);
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return TOKEN_TYPE_REFRESH.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    public record GeneratedToken(String token, String tokenId, Instant expiresAt) {
    }
}
