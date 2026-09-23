package com.gyeote.gyeote_api.domain.auth.service;

import com.gyeote.gyeote_api.domain.auth.dto.*;
import com.gyeote.gyeote_api.domain.auth.exception.AuthErrorCode;
import com.gyeote.gyeote_api.domain.auth.social.SocialTokenVerifier;
import com.gyeote.gyeote_api.domain.auth.social.SocialUserInfo;
import com.gyeote.gyeote_api.domain.device.repository.DeviceTokenRepository;
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

/**
 * 일반 로그인과 소셜 로그인을 처리하고 JWT 기반 인증 세션을 관리하는 서비스입니다.
 *
 * <p>로그인에 성공하면 Access Token과 Refresh Token을 발급하고, Refresh Token은
 * 사용자별 Redis 키에 저장합니다. 새 토큰을 발급할 때 기존 Refresh Token을 덮어쓰므로
 * 사용자당 하나의 유효한 갱신 세션만 유지됩니다.</p>
 *
 * <p>로그아웃 시 Redis에 저장된 Refresh Token과 서버에 등록된 사용자의 FCM 디바이스
 * 토큰을 삭제합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** Redis에 Refresh Token을 저장할 때 사용하는 키 접두사 */
    private static final String REFRESH_KEY_PREFIX = "refresh:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final OnboardingTokenProvider onboardingTokenProvider;
    private final SocialTokenVerifier socialTokenVerifier;
    private final DeviceTokenRepository deviceTokenRepository;

    /**
     * 로그인 ID와 비밀번호를 검증하여 사용자를 로그인시킵니다.
     *
     * <p>계정 존재 여부가 노출되지 않도록 비밀번호 검증을 마친 후 계정 상태를 확인합니다.
     * 로그인에 성공하면 마지막 로그인 시각을 갱신하고 새로운 Access Token과 Refresh Token을
     * 발급합니다.</p>
     *
     * @param request 로그인 ID와 비밀번호가 포함된 요청
     * @return 새로 발급한 Access Token과 Refresh Token
     * @throws BusinessException 로그인 정보가 일치하지 않거나 계정이 비활성 상태인 경우
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
     * Refresh Token을 검증하고 새로운 인증 토큰을 발급합니다.
     *
     * <p>JWT의 유효성뿐만 아니라 Redis에 저장된 Refresh Token과의 일치 여부, 사용자 존재 여부,
     * 계정 활성 상태를 확인합니다. 저장된 토큰과 요청 토큰이 다르거나 유효한 사용자를 찾을 수
     * 없는 경우 기존 Redis 세션을 삭제합니다. 검증에 성공하면 기존 Refresh Token을 새 토큰으로
     * 교체합니다.</p>
     *
     * @param request 재발급에 사용할 Refresh Token 요청
     * @return 새로 발급한 Access Token과 Refresh Token
     * @throws BusinessException 토큰 또는 사용자 세션이 유효하지 않거나 계정이 비활성 상태인 경우
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
     * Refresh Token을 해석하여 사용자 ID를 반환합니다.
     *
     * <p>만료된 토큰과 형식 또는 서명이 유효하지 않은 토큰을 서로 다른 인증 오류로 변환합니다.</p>
     *
     * @param refreshToken 해석할 Refresh Token
     * @return 토큰의 subject에 포함된 사용자 ID
     * @throws BusinessException Refresh Token이 만료되었거나 유효하지 않은 경우
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
     * 사용자의 토큰 갱신 세션과 푸시 알림 수신 정보를 제거하여 로그아웃 처리합니다.
     *
     * <p>사용자에게 등록된 FCM 디바이스 토큰을 삭제하고 Redis에 저장된 Refresh Token을
     * 제거합니다. 이미 삭제된 정보가 있어도 예외 없이 처리됩니다. 기존에 발급된 Access Token은
     * 별도로 폐기하지 않으며 자체 유효 시간이 끝날 때까지 유지됩니다.</p>
     *
     * @param userId 로그아웃할 사용자 ID
     */
    @Transactional
    public void logout(Long userId) {
        deviceTokenRepository.deleteByUserId(userId);
        redisTemplate.delete(REFRESH_KEY_PREFIX + userId);
    }

    /**
     * 사용자에게 새로운 Access Token과 Refresh Token을 발급합니다.
     *
     * <p>발급한 Refresh Token은 설정된 유효 시간 동안 Redis에 저장되며, 해당 사용자의
     * 기존 Refresh Token이 있으면 새로운 값으로 교체됩니다.</p>
     *
     * @param userId 토큰을 발급할 사용자 ID
     * @return 새로 발급한 Access Token과 Refresh Token
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
     * 소셜 제공자 토큰을 검증하고 가입 여부에 따라 로그인 또는 추가 가입 절차를 시작합니다.
     *
     * <p>소셜 계정의 가입 이력이 없으면 추가 회원가입에 사용할 Onboarding Token을 발급합니다.
     * 가입된 활성 사용자라면 마지막 로그인 시각을 갱신하고 Access Token과 Refresh Token을
     * 발급합니다.</p>
     *
     * @param socialLoginRequest 소셜 제공자와 제공자 인증 토큰이 포함된 요청
     * @return 추가 가입이 필요하면 Onboarding Token, 로그인에 성공하면 인증 토큰
     * @throws BusinessException 소셜 토큰이 유효하지 않거나 가입된 계정이 비활성 상태인 경우
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
