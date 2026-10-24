package com.gyeote.gyeote_api.domain.auth.exception;

import com.gyeote.gyeote_api.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    AUTH_INVALID_EMAIL_CODE(HttpStatus.BAD_REQUEST, "인증번호가 일치하지 않거나 만료되었습니다."),
    AUTH_EMAIL_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "인증번호는 60초 후 다시 요청해주세요."),
    AUTH_EMAIL_DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "이메일 인증 요청의 일일 한도를 초과했습니다."),
    AUTH_EMAIL_DELIVERY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "현재 인증 메일을 처리할 수 없습니다. 잠시 후 다시 시도해주세요."),
    AUTH_INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST, "비밀번호 재설정 인증이 유효하지 않거나 만료되었습니다."),

    AUTH_DEACTIVATED_USER(HttpStatus.FORBIDDEN, "비활성화된 계정입니다."),
    AUTH_LOGGED_IN_OTHER_DEVICE(HttpStatus.UNAUTHORIZED, "다른 기기에서 로그인되어 현재 세션이 만료되었습니다."),
    AUTH_SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "세션이 만료되었습니다. 다시 로그인해주세요."),
    AUTH_REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "로그인 세션을 찾을 수 없습니다."),
    AUTH_EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 만료되었습니다."),
    AUTH_INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."),
    AUTH_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    AUTH_INVALID_LOGIN_INFO(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 일치하지 않습니다."),
    AUTH_PROVIDER_TOKEN_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "유효하지 않은 프로바이더 토큰입니다."),
    AUTH_UNSUPPORTED_SOCIAL_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인 제공자입니다."),
    AUTH_SOCIAL_CLIENT_ID_NOT_CONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR, "소셜 로그인 클라이언트 ID 설정이 누락되었습니다.");

    private final HttpStatus status;
    private final String message;
}
