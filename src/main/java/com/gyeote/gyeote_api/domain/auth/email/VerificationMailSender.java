package com.gyeote.gyeote_api.domain.auth.email;

public interface VerificationMailSender {
    void send(String email, String code, EmailVerificationPurpose purpose);
}
