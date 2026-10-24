package com.gyeote.gyeote_api.domain.auth.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDeliveryService {
    private final VerificationMailSender mailSender;

    @Async("emailDeliveryExecutor")
    public void send(String email, String code, EmailVerificationPurpose purpose) {
        try {
            mailSender.send(email, code, purpose);
        } catch (RuntimeException e) {
            // SMTP 예외 원문은 주소 및 인증 정보를 포함할 수 있으므로 기록하지 않는다.
            log.error("EMAIL_DELIVERY_FAILED: purpose={}, exception={}", purpose, e.getClass().getSimpleName());
        }
    }
}
