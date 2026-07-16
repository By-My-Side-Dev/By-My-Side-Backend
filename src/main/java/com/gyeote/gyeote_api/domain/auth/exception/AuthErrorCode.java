package com.gyeote.gyeote_api.domain.auth.exception;

import com.gyeote.gyeote_api.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

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
