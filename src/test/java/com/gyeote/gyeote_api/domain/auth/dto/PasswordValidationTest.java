package com.gyeote.gyeote_api.domain.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordValidationTest {

    private static Validator validator;

    @BeforeAll
    static void 검증기를_초기화한다() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void 길이_제한_내에서_영문과_숫자를_포함한_비밀번호는_검증을_통과한다() {
        assertThat(비밀번호_검증_메시지_조회(회원가입_요청_생성("password1234"))).isEmpty();
        assertThat(비밀번호_검증_메시지_조회(소셜_회원가입_요청_생성("password1234"))).isEmpty();
    }

    @Test
    void 비밀번호_규칙을_위반하면_규칙별_오류_메시지를_반환한다() {
        assertThat(비밀번호_검증_메시지_조회(회원가입_요청_생성("abc123")))
                .contains("SIGNUP_PASSWORD_TOO_SHORT");
        assertThat(비밀번호_검증_메시지_조회(회원가입_요청_생성("a".repeat(64) + "1")))
                .contains("SIGNUP_PASSWORD_TOO_LONG");
        assertThat(비밀번호_검증_메시지_조회(회원가입_요청_생성("12345678")))
                .contains("SIGNUP_PASSWORD_LETTER_REQUIRED");
        assertThat(비밀번호_검증_메시지_조회(회원가입_요청_생성("abcdefgh")))
                .contains("SIGNUP_PASSWORD_NUMBER_REQUIRED");
    }

    @Test
    void 소셜_회원가입에도_동일한_비밀번호_규칙을_적용한다() {
        assertThat(비밀번호_검증_메시지_조회(소셜_회원가입_요청_생성("12345678")))
                .contains("SIGNUP_PASSWORD_LETTER_REQUIRED");
        assertThat(비밀번호_검증_메시지_조회(소셜_회원가입_요청_생성("abcdefgh")))
                .contains("SIGNUP_PASSWORD_NUMBER_REQUIRED");
    }

    private Set<String> 비밀번호_검증_메시지_조회(Object request) {
        return validator.validate(request).stream()
                .filter(violation -> violation.getPropertyPath().toString().equals("password"))
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }

    private SignupRequest 회원가입_요청_생성(String password) {
        return new SignupRequest(
                "홍길동", "login-id", "user@example.com", "email-verification-token", password, "01012345678",
                true, true, true, true
        );
    }

    private SocialSignupCompleteRequest 소셜_회원가입_요청_생성(String password) {
        return new SocialSignupCompleteRequest(
                "onboarding-token", "홍길동", "login-id", password, "01012345678",
                true, true, true, true
        );
    }
}
