package com.gyeote.gyeote_api.domain.auth.service;

import com.gyeote.gyeote_api.domain.auth.dto.LoginRequest;
import com.gyeote.gyeote_api.domain.auth.dto.RefreshRequest;
import com.gyeote.gyeote_api.domain.auth.dto.TokenResponse;
import com.gyeote.gyeote_api.domain.auth.exception.AuthErrorCode;
import com.gyeote.gyeote_api.domain.user.entity.User;
import com.gyeote.gyeote_api.domain.user.entity.UserStatus;
import com.gyeote.gyeote_api.domain.user.repository.UserRepository;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import com.gyeote.gyeote_api.global.security.JwtTokenProvider;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_KEY_PREFIX = "refresh:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;

    /**
     * 로그인 로직
     * @param request
     * @return 토큰 반환
     */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByLoginIdAndDeletedAtIsNull(request.loginId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.AUTH_INVALID_LOGIN_INFO));

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.AUTH_INVALID_LOGIN_INFO);
        }

        // 비밀번호 검증 후에 상태를 확인해야 계정 존재 여부가 노출되지 않는다
        if (user.getStatus() == UserStatus.DEACTIVE) {
            throw new BusinessException(AuthErrorCode.AUTH_DEACTIVATED_USER);
        }

        user.updateLastLoginAt(LocalDateTime.now());
        return issueTokens(user.getId());
    }

    /**
     * 리프레쉬 토큰 확인(검문소)
     * @param request
     * @return issueTokens 메소드로 userId 반환
     */
    public TokenResponse refresh(RefreshRequest request) {
        Long userId = parseRefreshToken(request.refreshToken());

        String key = REFRESH_KEY_PREFIX + userId;
        String savedToken = redisTemplate.opsForValue().get(key);

        if (savedToken == null) {
            throw new BusinessException(AuthErrorCode.AUTH_REFRESH_TOKEN_NOT_FOUND);
        }
        if (!request.refreshToken().equals(savedToken)) {
            redisTemplate.delete(key);
            throw new BusinessException(AuthErrorCode.AUTH_LOGGED_IN_OTHER_DEVICE);
        }

        return issueTokens(userId);
    }

    /**
     * 에러처리 세분화 메소드
     * @param refreshToken
     * @return 유저 아이디 반환
     */
    private Long parseRefreshToken(String refreshToken) {
        try {
            return jwtTokenProvider.parseUserIdOrThrow(refreshToken, JwtTokenProvider.TYPE_REFRESH);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(AuthErrorCode.AUTH_EXPIRED_REFRESH_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(AuthErrorCode.AUTH_INVALID_REFRESH_TOKEN);
        }
    }

    /**
     * 유저를 로그아웃 시키는 메소드
     * @param userId
     */
    public void logout(Long userId) {
        redisTemplate.delete(REFRESH_KEY_PREFIX + userId);
    }

    /**
     * 새로운 토블 발급(발급소)
     * @param userId
     * @return 토큰을 생성해서 반환
     */
    private TokenResponse issueTokens(Long userId) {
        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);

        // 리프레쉬 토큰 덮어쓰기(redis)
        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + userId,
                refreshToken,
                Duration.ofMillis(jwtTokenProvider.getRefreshTokenValidityMs())
        );

        return new TokenResponse(accessToken, refreshToken,
                jwtTokenProvider.getAccessTokenValidityMs() / 1000);
    }
}
