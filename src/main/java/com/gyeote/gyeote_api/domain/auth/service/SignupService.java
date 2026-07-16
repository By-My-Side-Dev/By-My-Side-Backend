package com.gyeote.gyeote_api.domain.auth.service;

import com.gyeote.gyeote_api.domain.auth.dto.OnboardingInfo;
import com.gyeote.gyeote_api.domain.auth.dto.SignupRequest;
import com.gyeote.gyeote_api.domain.auth.dto.SocialSignupCompleteRequest;
import com.gyeote.gyeote_api.domain.auth.dto.SocialSignupCompleteResponse;
import com.gyeote.gyeote_api.domain.auth.exception.AuthErrorCode;
import com.gyeote.gyeote_api.domain.auth.exception.SignupErrorCode;
import com.gyeote.gyeote_api.domain.user.entity.User;
import com.gyeote.gyeote_api.domain.user.entity.UserPhone;
import com.gyeote.gyeote_api.domain.user.entity.UserStatus;
import com.gyeote.gyeote_api.domain.user.repository.UserPhoneRepository;
import com.gyeote.gyeote_api.domain.user.repository.UserRepository;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import com.gyeote.gyeote_api.global.security.OnboardingTokenProvider;
import com.gyeote.gyeote_api.global.security.PhoneCryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 자체 회원가입 (DB 명세서 v1.1.0 - 3.1 users, 3.2 user_phones).
 * 전화번호는 users가 아닌 user_phones에 암호값 + 블라인드 인덱스로만 저장한다.
 * TODO: SMS 전화번호 인증 기능 구현 시 verify:ok:{번호} 선행 확인 + 가입 후 키 삭제를 복원할 것.
 */
@Service
@RequiredArgsConstructor
public class SignupService {

    private static final int MIN_PHONE_DIGITS = 10;
    private static final int MAX_PHONE_DIGITS = 15;
    private static final int RECLAIM_DORMANT_DAYS = 60;

    private final UserRepository userRepository;
    private final UserPhoneRepository userPhoneRepository;
    private final PasswordEncoder passwordEncoder;
    private final PhoneCryptoService phoneCryptoService;
    private final OnboardingTokenProvider onboardingTokenProvider;
    private final AuthService authService;

    @Transactional
    public void signup(SignupRequest request) {
        String phone = normalizeAndValidatePhone(request.phone());

        if (userRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException(SignupErrorCode.SIGNUP_DUPLICATED_LOGIN_ID);
        }

        String phoneHash = validateAvailablePhoneAndGetHash(phone);

        User user = User.builder()
                .name(request.name())
                .loginId(request.loginId())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();
        user.activate();
        userRepository.save(user);
        saveUserPhone(user, phone, phoneHash);
    }

    @Transactional
    public SocialSignupCompleteResponse completeSocialSignup(SocialSignupCompleteRequest request) {
        OnboardingInfo info = onboardingTokenProvider.parseOnboardingToken(request.onboardingToken());
        String provider = info.provider();
        String providerUid = info.providerUid();

        userRepository.findByProviderAndProviderUid(provider, providerUid)
                .ifPresent(user -> {
                    throw new BusinessException(SignupErrorCode.SIGNUP_DUPLICATED_SOCIAL_ACCOUNT);
                });

        return userRepository.findByLoginIdAndDeletedAtIsNull(request.loginId())
                .map(user -> linkSocialAccount(user, request.password(), provider, providerUid))
                .orElseGet(() -> signupSocialAccount(request, provider, providerUid));
    }

    private SocialSignupCompleteResponse linkSocialAccount(
            User user,
            String password,
            String provider,
            String providerUid
    ) {
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.AUTH_INVALID_LOGIN_INFO);
        }
        if (user.getStatus() == UserStatus.DEACTIVE) {
            throw new BusinessException(AuthErrorCode.AUTH_DEACTIVATED_USER);
        }
        if (user.getProvider() != null || user.getProviderUid() != null) {
            throw new BusinessException(SignupErrorCode.SIGNUP_DUPLICATED_SOCIAL_ACCOUNT);
        }

        user.linkSocialAccount(provider, providerUid);
        user.updateLastLoginAt(LocalDateTime.now());
        return new SocialSignupCompleteResponse(authService.issueTokens(user.getId()));
    }

    private SocialSignupCompleteResponse signupSocialAccount(
            SocialSignupCompleteRequest request,
            String provider,
            String providerUid
    ) {
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException(SignupErrorCode.SIGNUP_DUPLICATED_LOGIN_ID);
        }

        String phone = normalizeAndValidatePhone(request.phone());
        String phoneHash = validateAvailablePhoneAndGetHash(phone);

        User user = User.builder()
                .name(request.name())
                .loginId(request.loginId())
                .passwordHash(passwordEncoder.encode(request.password()))
                .provider(provider)
                .providerUid(providerUid)
                .build();
        user.activate();
        userRepository.save(user);
        saveUserPhone(user, phone, phoneHash);

        return new SocialSignupCompleteResponse(authService.issueTokens(user.getId()));
    }

    private String normalizeAndValidatePhone(String phone) {
        String normalizedPhone = phoneCryptoService.normalize(phone);
        validatePhone(normalizedPhone);
        return normalizedPhone;
    }

    private String validateAvailablePhoneAndGetHash(String phone) {
        String phoneHash = phoneCryptoService.hash(phone);
        userPhoneRepository.findByActivePhoneHash(phoneHash)
                .ifPresent(this::rejectOccupiedPhone);
        return phoneHash;
    }

    private void saveUserPhone(User user, String phone, String phoneHash) {
        userPhoneRepository.save(UserPhone.builder()
                .user(user)
                .phoneEncrypted(phoneCryptoService.encrypt(phone))
                .phoneHash(phoneHash)
                .build());
    }

    private void validatePhone(String normalizedPhone) {
        if (normalizedPhone.length() < MIN_PHONE_DIGITS || normalizedPhone.length() > MAX_PHONE_DIGITS) {
            throw new BusinessException(SignupErrorCode.SIGNUP_INVALID_PHONE);
        }
    }

    /**
     * 이미 활성 상태로 점유된 번호 처리.
     * 기존 계정이 60일 이상 무로그인(휴면)이면 reclaim 게이트로 안내하고, 활동 중이면 중복 가입으로 거부한다.
     * 실제 점유 해제(reclaim) 절차는 별도 이슈에서 구현한다.
     */
    private void rejectOccupiedPhone(UserPhone activePhone) {
        User owner = activePhone.getUser();
        LocalDateTime lastActivity = owner.getLastLoginAt() != null
                ? owner.getLastLoginAt()
                : owner.getCreatedAt();

        if (lastActivity.isBefore(LocalDateTime.now().minusDays(RECLAIM_DORMANT_DAYS))) {
            throw new BusinessException(SignupErrorCode.SIGNUP_RECLAIM_REQUIRED);
        }
        throw new BusinessException(SignupErrorCode.SIGNUP_DUPLICATED_PHONE);
    }
}
