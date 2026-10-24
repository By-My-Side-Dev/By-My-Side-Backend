package com.gyeote.gyeote_api.domain.auth.exception;

import com.gyeote.gyeote_api.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SignupErrorCode implements ErrorCode {

    SIGNUP_DUPLICATED_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    SIGNUP_EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "이메일 인증이 완료되지 않았거나 만료되었습니다."),

    SIGNUP_DUPLICATED_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    SIGNUP_DUPLICATED_SOCIAL_ACCOUNT(HttpStatus.CONFLICT, "이미 가입된 소셜 계정입니다."),
    SIGNUP_PHONE_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "전화번호 인증이 완료되지 않았습니다."),
    SIGNUP_REQUIRED_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호는 8자 이상이어야 합니다."),
    SIGNUP_PASSWORD_TOO_SHORT(HttpStatus.BAD_REQUEST, "비밀번호는 8자 이상이어야 합니다."),
    SIGNUP_PASSWORD_TOO_LONG(HttpStatus.BAD_REQUEST, "비밀번호는 64자 이하여야 합니다."),
    SIGNUP_PASSWORD_LETTER_REQUIRED(HttpStatus.BAD_REQUEST, "비밀번호에 영문자를 포함해주세요."),
    SIGNUP_PASSWORD_NUMBER_REQUIRED(HttpStatus.BAD_REQUEST, "비밀번호에 숫자를 포함해주세요."),
    SIGNUP_REQUIRED_PHONE(HttpStatus.BAD_REQUEST, "전화번호를 입력해주세요."),
    SIGNUP_INVALID_PHONE(HttpStatus.BAD_REQUEST, "전화번호 형식이 올바르지 않습니다."),
    SIGNUP_INVALID_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인 제공자입니다.");

    private final HttpStatus status;
    private final String message;
}
