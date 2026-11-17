package com.mathverse.security;

import com.mathverse.config.Settings;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Login tokens: a JWT signed with HS256 holding {"sub": user id, "exp": expiry}.
 * The same format as the Python backend (security.py).
 */
@Component
public class Tokens {

    private final SecretKey key;
    private final int expireMinutes;

    public Tokens(Settings settings) {
        key = Keys.hmacShaKeyFor(settings.jwtSecret().getBytes(StandardCharsets.UTF_8));
        expireMinutes = settings.tokenExpireMinutes();
    }

    public String create(long userId) {
        Instant expires = Instant.now().plus(expireMinutes, ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .expiration(Date.from(expires))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** The user id inside a valid, unexpired token; null for anything else. */
    public Long userIdFrom(String token) {
        try {
            String subject = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Long.valueOf(subject);
        } catch (JwtException | IllegalArgumentException e) {
            // Bad signature, expired, malformed, or no numeric "sub".
            return null;
        }
    }
}
