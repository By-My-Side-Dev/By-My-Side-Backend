package com.gyeote.gyeote_api.global.exception;

import lombok.Getter;

/**
 * 모든 도메인 예외의 부모 클래스.
 * 도메인별 예외는 이 클래스를 상속하면 GlobalExceptionHandler가 자동으로 처리한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
