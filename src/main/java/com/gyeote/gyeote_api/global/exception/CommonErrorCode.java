package com.gyeote.gyeote_api.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    COMMON_INVALID_REQUEST(HttpStatus.BAD_REQUEST,"요청값이 올바르지 않습니다."),
    COMMON_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값 검증에 실패했습니다."),
    COMMON_UNAUTHORIZED(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다."),
    COMMON_FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    COMMON_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    COMMON_METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    COMMON_UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
    COMMON_CONFLICT(HttpStatus.CONFLICT, "이미 처리되었거나 중복된 요청입니다."),
    COMMON_OPTIMISTIC_LOCK_CONFLICT(HttpStatus.CONFLICT, "다른 요청이 먼저 처리했습니다. 다시 시도해 주세요."),
    COMMON_TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청 횟수가 너무 많습니다."),
    COMMON_INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    ;

    private final HttpStatus status;
    private final String message;
}
