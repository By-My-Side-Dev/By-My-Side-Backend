package com.gyeote.gyeote_api.domain.user.exception;

import com.gyeote.gyeote_api.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {
    USER_PROFILE_IMAGE_EMPTY(HttpStatus.BAD_REQUEST, "프로필 이미지 파일이 비어있습니다."),
    USER_PROFILE_IMAGE_MAX(HttpStatus.BAD_REQUEST, "프로필 이미지 크기는 10MB 를 넘을 수 없습니다."),
    USER_PROFILE_IMAGE_UNSUPPORTED_TYPE(HttpStatus.BAD_REQUEST, "올바른 프로필 확장자가 아닙니다."),
    USER_PROFILE_IMAGE_READ_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 파일을 읽기에 실패했습니다."),
    ;

    private final HttpStatus status;
    private final String message;
}
