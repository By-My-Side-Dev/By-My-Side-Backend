package com.gyeote.gyeote_api.domain.auth.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@ConditionalOnProperty(name = "mail.enabled", havingValue = "true")
public class SmtpVerificationMailSender implements VerificationMailSender {
    private final JavaMailSender mailSender;
    private final String from;

    public SmtpVerificationMailSender(JavaMailSender mailSender, @Value("${mail.from}") String from) {
        Assert.hasText(from, "MAIL_FROM 설정이 필요합니다.");
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String email, String code, EmailVerificationPurpose purpose) {
        String action = purpose == EmailVerificationPurpose.SIGNUP ? "회원가입" : "비밀번호 재설정";
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("[GYEOTE] " + action + " 인증번호");
        message.setText(action + " 인증번호는 " + code + "입니다.\n5분 이내에 입력해주세요.\n본인이 요청하지 않았다면 이 메일을 무시해주세요.");
        mailSender.send(message);
    }
}
