package com.gyeote.gyeote_api.domain.auth.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "mail.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpVerificationMailSender implements VerificationMailSender {
    @Override
    public void send(String email, String code, EmailVerificationPurpose purpose) {
        // 로컬에서도 이메일, 인증번호, 토큰을 로그로 노출하지 않는다.
    }
}
