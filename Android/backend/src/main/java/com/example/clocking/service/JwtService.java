package com.example.clocking.service;

import com.example.clocking.config.JwtProperties;
import com.example.clocking.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    /** Skew grande solo para POST /auth/refresh: JWT caducado pero firma válida y sesión viva en BD. */
    private static final long REFRESH_CLOCK_SKEW_SECONDS = 365L * 24 * 60 * 60;

    private final SecretKey key;
    private final long expirationMs;
    private final Clock clock;

    public JwtService(JwtProperties jwtProperties, Clock clock) {
        byte[] secretBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expirationMs = jwtProperties.getExpirationMs();
        this.clock = clock;
    }

    /** Misma caducidad que la fila de sesión (evita ventana JWT válida + sesión caducada en BD). */
    public String generateToken(User user, long sessionId, Instant expiresAt) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("sid", sessionId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    public Optional<TokenPayload> parseToken(String token) {
        try {
            var payload = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return toPayload(payload);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Para renovar: acepta JWT ya expirado si la firma es válida (sesión se valida después en BD). */
    public Optional<TokenPayload> parseTokenForRefresh(String token) {
        try {
            var payload = Jwts.parser()
                    .verifyWith(key)
                    .clockSkewSeconds(REFRESH_CLOCK_SKEW_SECONDS)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return toPayload(payload);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Optional<TokenPayload> toPayload(Claims payload) {
        try {
            String sub = payload.getSubject();
            Object sidObj = payload.get("sid");
            long sid = sidObj instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(sidObj));
            Instant exp = payload.getExpiration().toInstant();
            return Optional.of(new TokenPayload(Long.parseLong(sub), sid, exp));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public record TokenPayload(long userId, long sessionId, Instant expiresAt) {}
}
