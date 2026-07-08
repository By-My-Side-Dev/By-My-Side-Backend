package com.gyeote.gyeote_api.domain.auth.service;

import com.gyeote.gyeote_api.domain.auth.dto.*;
import com.gyeote.gyeote_api.domain.auth.exception.AuthErrorCode;
import com.gyeote.gyeote_api.domain.auth.social.SocialTokenVerifier;
import com.gyeote.gyeote_api.domain.auth.social.SocialUserInfo;
import com.gyeote.gyeote_api.domain.user.entity.User;
import com.gyeote.gyeote_api.domain.user.entity.UserStatus;
import com.gyeote.gyeote_api.domain.user.repository.UserRepository;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import com.gyeote.gyeote_api.global.security.JwtTokenProvider;
import com.gyeote.gyeote_api.global.security.OnboardingTokenProvider;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_KEY_PREFIX = "refresh:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final OnboardingTokenProvider onboardingTokenProvider;
    private final SocialTokenVerifier socialTokenVerifier;

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
    @Transactional
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

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> {
                    redisTemplate.delete(key);
                    return new BusinessException(AuthErrorCode.AUTH_SESSION_EXPIRED);
                });

        if (user.getStatus() == UserStatus.DEACTIVE) {
            redisTemplate.delete(key);
            throw new BusinessException(AuthErrorCode.AUTH_DEACTIVATED_USER);
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
     * 새로운 토큰 발급(발급소)
     * @param userId
     * @return 액세스 토큰, 리프레시 토큰 반환
     */
    public TokenResponse issueTokens(Long userId) {
        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);

        // 리프레쉬 토큰 덮어쓰기(redis)
        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + userId,
                refreshToken,
                Duration.ofMillis(jwtTokenProvider.getRefreshTokenValidityMs())
        );

        return new TokenResponse(accessToken, refreshToken);
    }

    /**
     * 소셜 로그인 로직
     * @param socialLoginRequest
     * @return 액세스 토큰, 리프레시 토큰 반환
     */
    @Transactional
    public SocialLoginResponse socialLogin(SocialLoginRequest socialLoginRequest) {
        SocialUserInfo socialUserInfo = socialTokenVerifier.verify(
                socialLoginRequest.provider(),
                socialLoginRequest.providerToken()
        );

        Optional<User> user = userRepository.findByProviderAndProviderUid(
                socialUserInfo.provider().storageValue(),
                socialUserInfo.providerUid()
        );

        // [분기 1] provider + providerUid 가입 이력이 DB에 존재하지 않을 때 회원가입 유도
        if (user.isEmpty()) {
            String onboardingToken = onboardingTokenProvider.createOnboardingToken(
                    socialUserInfo.provider().storageValue(),
                    socialUserInfo.providerUid()
            );
            return SocialLoginResponse.signupRequired(onboardingToken);
        }

        User loginUser = user.get();
        if (loginUser.getStatus() == UserStatus.DEACTIVE) {
            throw new BusinessException(AuthErrorCode.AUTH_DEACTIVATED_USER);
        }

        loginUser.updateLastLoginAt(LocalDateTime.now());

        // [분기 2] provider + providerUid 가 있는 경우 AccessToken RefreshToken 발급
        return SocialLoginResponse.loginSuccess(issueTokens(loginUser.getId()));
    }

}
