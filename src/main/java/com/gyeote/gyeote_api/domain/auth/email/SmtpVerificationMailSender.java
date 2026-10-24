package com.gyeote.gyeote_api.domain.auth.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.mail.MessagingException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "mail.enabled", havingValue = "true")
public class SmtpVerificationMailSender implements VerificationMailSender {
    private final JavaMailSender mailSender;
    private final String from;
    private final String htmlTemplate;

    public SmtpVerificationMailSender(JavaMailSender mailSender, @Value("${mail.from}") String from) throws IOException {
        Assert.hasText(from, "MAIL_FROM 설정이 필요합니다.");
        this.mailSender = mailSender;
        this.from = from;
        this.htmlTemplate = new ClassPathResource("mail/email-verification.html")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public void send(String email, String code, EmailVerificationPurpose purpose) {
        String action = purpose == EmailVerificationPurpose.SIGNUP ? "회원가입" : "비밀번호 재설정";
        String description = purpose == EmailVerificationPurpose.SIGNUP
                ? "내곁에와 함께할 준비가 거의 끝났어요.\n앱에 아래 인증번호를 입력해 이메일 인증을 완료해주세요."
                : "비밀번호 재설정을 요청하셨나요?\n앱에 아래 인증번호를 입력해 본인 확인을 완료해주세요.";
        String plainText = "[곁에] 인증번호는 " + code + "입니다.";
        String html = htmlTemplate
                .replace("{{action}}", HtmlUtils.htmlEscape(action))
                .replace("{{description}}", HtmlUtils.htmlEscape(description))
                .replace("{{code}}", HtmlUtils.htmlEscape(code));
        var message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject("[내곁에] " + action + " 인증번호");
            helper.setText(plainText, html);
        } catch (MessagingException e) {
            throw new MailPreparationException("인증 메일 본문 구성에 실패했습니다.", e);
        }
        mailSender.send(message);
    }
}
