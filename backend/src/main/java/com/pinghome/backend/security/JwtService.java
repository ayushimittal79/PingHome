package com.pinghome.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

/**
 * Issues and validates the JWTs the Android app carries as a Bearer token on every
 * authenticated request. The secret must be overridden via PINGHOME_JWT_SECRET in any
 * non-local environment; the default in application.yml only makes local development work
 * out of the box, and the deployed profile refuses to start with it (see application-cloud.yml).
 */
@Service
public class JwtService {

    /** Must match the fallback in application.yml - it is public knowledge, so it must never sign real tokens. */
    static final String LOCAL_DEV_DEFAULT_SECRET = "pinghome-local-development-secret-change-me-please-32bytes+";

    private final SecretKey key;
    private final long expirationMinutes;
    private final String issuer;

    public JwtService(
            @Value("${pinghome.jwt.secret}") String secret,
            @Value("${pinghome.jwt.expiration-minutes:10080}") long expirationMinutes,
            @Value("${pinghome.jwt.issuer:pinghome-backend}") String issuer,
            @Value("${pinghome.jwt.reject-default-secret:false}") boolean rejectDefaultSecret
    ) {
        if (rejectDefaultSecret && LOCAL_DEV_DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "PINGHOME_JWT_SECRET is not set - refusing to start a deployed instance with the "
                    + "checked-in development secret. Generate one with: openssl rand -base64 48");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
        this.issuer = issuer;
    }

    public String generateToken(Long userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(email)
                .claims(Map.of("uid", userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationMinutes * 60)))
                .signWith(key)
                .compact();
    }

    public Optional<Claims> parseClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public String extractEmail(Claims claims) {
        return claims.getSubject();
    }

    public Long extractUserId(Claims claims) {
        return claims.get("uid", Long.class);
    }
}
