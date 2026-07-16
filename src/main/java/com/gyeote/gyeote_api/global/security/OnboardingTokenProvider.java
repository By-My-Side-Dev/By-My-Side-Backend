package com.gyeote.gyeote_api.global.security;

import com.gyeote.gyeote_api.domain.auth.dto.OnboardingInfo;
import com.gyeote.gyeote_api.domain.auth.exception.AuthErrorCode;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;


@Component
public class OnboardingTokenProvider {

    private final SecretKey key;

    public OnboardingTokenProvider(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createOnboardingToken(String provider, String providerUid) {
        return Jwts.builder()
                .claim("type", "ONBOARDING")
                .claim("provider", provider)
                .claim("providerUid", providerUid)
                .expiration(Date.from(Instant.now().plus(Duration.ofMinutes(15))))
                .signWith(key)
                .compact();

    }

    /**
     * 온보딩 토큰 검증
     * @param token
     * @return OnboardingInfo(provider, providerUid) 반환
     */
    public OnboardingInfo parseOnboardingToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!"ONBOARDING".equals(claims.get("type", String.class))) {
                throw new JwtException("token type mismatch");
            }

            String provider = claims.get("provider", String.class);
            String providerUid = claims.get("providerUid", String.class);

            if (provider == null || provider.isBlank()
                    || providerUid == null || providerUid.isBlank()) {
                throw new JwtException("missing onboarding claims");
            }

            return new OnboardingInfo(provider, providerUid);
        } catch (JwtException | IllegalArgumentException e) {
            throw new
                    BusinessException(AuthErrorCode.AUTH_PROVIDER_TOKEN_UNAUTHORIZED);
        }
    }

}
