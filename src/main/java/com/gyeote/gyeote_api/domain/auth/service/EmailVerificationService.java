package com.gyeote.gyeote_api.domain.auth.service;

import com.gyeote.gyeote_api.domain.auth.email.EmailDeliveryService;
import com.gyeote.gyeote_api.domain.auth.email.EmailVerificationPurpose;
import com.gyeote.gyeote_api.domain.auth.exception.AuthErrorCode;
import com.gyeote.gyeote_api.domain.auth.exception.SignupErrorCode;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> ISSUE_CODE = script("redis/issue-email-code.lua");
    private static final DefaultRedisScript<Long> VERIFY_CODE = script("redis/verify-email-code.lua");
    private static final String SIGNUP_TOKEN_PREFIX = "email:signup-token:";
    private static final String RESET_TOKEN_PREFIX = "email:reset-token:";

    private final StringRedisTemplate redisTemplate;
    private final EmailDeliveryService emailDeliveryService;

    /** 계정 미일치 요청도 동일한 발송 제한을 적용하고 이전 코드는 무효화한다. */
    public void requestCode(String email, EmailVerificationPurpose purpose, boolean eligible) {
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        String day = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.BASIC_ISO_DATE);
        Long result = redisTemplate.execute(ISSUE_CODE, List.of(
                "email:cooldown:" + purpose + ":" + email,
                "email:daily:" + email + ":" + day,
                codeKey(purpose, email), attemptsKey(purpose, email)
        ), "60", "10", "86400", "300", eligible ? code : "");
        if (Long.valueOf(-1).equals(result)) {
            throw new BusinessException(AuthErrorCode.AUTH_EMAIL_COOLDOWN);
        }
        if (Long.valueOf(-2).equals(result)) {
            throw new BusinessException(AuthErrorCode.AUTH_EMAIL_DAILY_LIMIT);
        }
        if (!Long.valueOf(1).equals(result)) {
            throw new IllegalStateException("Unexpected email issuance result");
        }
        if (eligible) {
            try {
                emailDeliveryService.send(email, code, purpose);
            } catch (TaskRejectedException e) {
                // 재설정 요청은 큐 상태에 따라 계정 존재 여부를 노출하지 않는다.
                if (purpose == EmailVerificationPurpose.SIGNUP) {
                    throw new BusinessException(AuthErrorCode.AUTH_EMAIL_DELIVERY_UNAVAILABLE);
                }
            }
        }
    }

    public String verifySignup(String email, String code) {
        return verify(email, code, EmailVerificationPurpose.SIGNUP, email, SIGNUP_TOKEN_PREFIX);
    }

    public String verifyPasswordReset(String email, String code, Long userId) {
        return verify(email, code, EmailVerificationPurpose.PASSWORD_RESET, userId.toString(), RESET_TOKEN_PREFIX);
    }

    private String verify(String email, String code, EmailVerificationPurpose purpose,
                          String payload, String tokenPrefix) {
        String token = UUID.randomUUID().toString();
        Long result = redisTemplate.execute(VERIFY_CODE,
                List.of(codeKey(purpose, email), attemptsKey(purpose, email), tokenPrefix + token),
                code, payload, "600", "5");
        if (!Long.valueOf(1).equals(result)) {
            throw new BusinessException(AuthErrorCode.AUTH_INVALID_EMAIL_CODE);
        }
        return token;
    }

    public void consumeSignupToken(String token, String email) {
        String verifiedEmail = redisTemplate.opsForValue().getAndDelete(SIGNUP_TOKEN_PREFIX + token);
        if (!email.equals(verifiedEmail)) {
            throw new BusinessException(SignupErrorCode.SIGNUP_EMAIL_NOT_VERIFIED);
        }
    }

    public Long consumeResetToken(String token) {
        String userId = redisTemplate.opsForValue().getAndDelete(RESET_TOKEN_PREFIX + token);
        if (userId == null) {
            throw new BusinessException(AuthErrorCode.AUTH_INVALID_RESET_TOKEN);
        }
        return Long.valueOf(userId);
    }

    private String codeKey(EmailVerificationPurpose purpose, String email) {
        return "email:code:" + purpose + ":" + email;
    }

    private String attemptsKey(EmailVerificationPurpose purpose, String email) {
        return "email:attempts:" + purpose + ":" + email;
    }

    private static DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
