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
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsPasswordContainingLettersAndNumbersWithinLengthLimit() {
        assertThat(passwordMessages(signupRequest("password1234"))).isEmpty();
        assertThat(passwordMessages(socialSignupRequest("password1234"))).isEmpty();
    }

    @Test
    void rejectsPasswordThatViolatesEachRule() {
        assertThat(passwordMessages(signupRequest("abc123")))
                .contains("SIGNUP_PASSWORD_TOO_SHORT");
        assertThat(passwordMessages(signupRequest("a".repeat(64) + "1")))
                .contains("SIGNUP_PASSWORD_TOO_LONG");
        assertThat(passwordMessages(signupRequest("12345678")))
                .contains("SIGNUP_PASSWORD_LETTER_REQUIRED");
        assertThat(passwordMessages(signupRequest("abcdefgh")))
                .contains("SIGNUP_PASSWORD_NUMBER_REQUIRED");
    }

    @Test
    void appliesSameRulesToSocialSignup() {
        assertThat(passwordMessages(socialSignupRequest("12345678")))
                .contains("SIGNUP_PASSWORD_LETTER_REQUIRED");
        assertThat(passwordMessages(socialSignupRequest("abcdefgh")))
                .contains("SIGNUP_PASSWORD_NUMBER_REQUIRED");
    }

    private Set<String> passwordMessages(Object request) {
        return validator.validate(request).stream()
                .filter(violation -> violation.getPropertyPath().toString().equals("password"))
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }

    private SignupRequest signupRequest(String password) {
        return new SignupRequest(
                "홍길동", "login-id", password, "01012345678",
                true, true, true, true
        );
    }

    private SocialSignupCompleteRequest socialSignupRequest(String password) {
        return new SocialSignupCompleteRequest(
                "onboarding-token", "홍길동", "login-id", password, "01012345678",
                true, true, true, true
        );
    }
}
