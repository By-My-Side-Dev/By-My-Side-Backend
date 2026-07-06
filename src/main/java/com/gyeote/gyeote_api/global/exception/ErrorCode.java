package com.gyeote.gyeote_api.global.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

    HttpStatus getStatus();

    String getMessage();

    /**
     * enum이 자동으로 구현하는 메서드.
     * 구현체는 반드시 enum이어야 한다.
     */
    String name();

    /**
     * enum 상수 이름을 그대로 에러 코드로 사용한다.
     */
    default String getCode() {
        return name();
    }
}
