package com.gyeote.gyeote_api.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private static final String TOKEN_TYPE_CLAIM = "type";

    private final SecretKey key;

    private final long accessTokenValidityMs;

    @Getter
    private final long refreshTokenValidityMs;

    /**
     * 토큰 제공자(환경변수에서 변수값 가져오기)
     * @param secret
     * @param accessTokenValidityMs
     * @param refreshTokenValidityMs
     */
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity-ms}") long accessTokenValidityMs,
            @Value("${jwt.refresh-token-validity-ms}") long refreshTokenValidityMs
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidityMs = accessTokenValidityMs;
        this.refreshTokenValidityMs = refreshTokenValidityMs;
    }

    /**
     * AccessToken 토큰 반환
     * @param userId
     * @return 액세스 토큰
     */
    public String createAccessToken(Long userId) {
        return createToken(userId, TYPE_ACCESS, accessTokenValidityMs);
    }

    /**
     * RefreshToken 토큰 반환
     * @param userId
     * @return 리프레쉬 토큰
     */
    public String createRefreshToken(Long userId) {
        return createToken(userId, TYPE_REFRESH, refreshTokenValidityMs);
    }

    /**
     * 유저에게 발급할 토큰 생성기
     * @param userId
     * @param type
     * @param validityMs
     * @return 토큰 반환
     */
    private String createToken(Long userId, String type, long validityMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TOKEN_TYPE_CLAIM, type)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validityMs))
                .signWith(key)
                .compact();
    }

    /**
     * 유저의 토큰을 검증하고 유저를 찾는 로직.</br>
     * 서명 불일치·만료·타입 불일치 등 모든 실패는 empty.
     * @param token
     * @param expectedType
     * @return 유효하면 userId, 실패 사유 무관하게 empty
     */
    public Optional<Long> parseUserId(String token, String expectedType) {
        try {
            return Optional.of(parseUserIdOrThrow(token, expectedType));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * 실패 사유를 구분해야 할 때 쓰는 검증 로직.</br>
     * 만료는 ExpiredJwtException, 그 외 무효(서명·타입·형식)는 JwtException을 던진다.
     * @param token
     * @param expectedType
     * @return userId
     */
    public Long parseUserIdOrThrow(String token, String expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (!expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new JwtException("token type mismatch: expected " + expectedType);
        }
        try {
            return Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new JwtException("invalid subject claim", e);
        }
    }

}
