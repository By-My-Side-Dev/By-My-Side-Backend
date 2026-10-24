package com.gyeote.gyeote_api.domain.auth.service;

import com.gyeote.gyeote_api.domain.auth.dto.*;
import com.gyeote.gyeote_api.domain.auth.email.EmailVerificationPurpose;
import com.gyeote.gyeote_api.domain.auth.exception.AuthErrorCode;
import com.gyeote.gyeote_api.domain.user.entity.User;
import com.gyeote.gyeote_api.domain.user.entity.UserStatus;
import com.gyeote.gyeote_api.domain.user.repository.UserRepository;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    /**
     * 아이디와 이메일이 있는지 검사하고 있으면 초기화 메일 발송
     * @param request 비밀번호 초기화 요청 객체
     */
    public void requestCode(PasswordResetCodeRequest request) {
        boolean eligible = findEligibleUser(request.loginId(), request.email()).isPresent();
        emailVerificationService.requestCode(request.email(), EmailVerificationPurpose.PASSWORD_RESET, eligible);
    }

    public PasswordResetTokenResponse verify(PasswordResetVerifyRequest request) {
        User user = findEligibleUser(request.loginId(), request.email())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.AUTH_INVALID_EMAIL_CODE));
        return new PasswordResetTokenResponse(
                emailVerificationService.verifyPasswordReset(request.email(), request.code(), user.getId()));
    }

    @Transactional
    public void confirm(PasswordResetConfirmRequest request) {
        String hash = passwordEncoder.encode(request.newPassword());
        Long userId = emailVerificationService.consumeResetToken(request.resetToken());
        User user = userRepository.findByIdForUpdate(userId)
                .filter(this::isActivePasswordUser)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.AUTH_INVALID_RESET_TOKEN));
        user.updatePasswordHash(hash);
        userRepository.flush();
        // Redis 오류 시 DB 변경도 롤백한다. Redis 삭제는 DB와 별도의 연산이다.
        redisTemplate.delete("refresh:" + userId);
    }

    private Optional<User> findEligibleUser(String loginId, String email) {
        return userRepository.findByLoginIdAndDeletedAtIsNull(loginId)
                .filter(this::isActivePasswordUser)
                .filter(user -> email.equals(user.getEmail()));
    }

    private boolean isActivePasswordUser(User user) {
        return user.getStatus() == UserStatus.ACTIVE && user.getDeletedAt() == null
                && user.getLoginId() != null && user.getPasswordHash() != null;
    }
}
