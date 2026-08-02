package com.gyeote.gyeote_api.domain.location.exception;

import com.gyeote.gyeote_api.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LocationErrorCode implements ErrorCode {
    LOCATION_UPDATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "위치 정보 갱신에 실패했습니다."),
    LOCATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 위치 정보에 접근할 수 없습니다."),
    LOCATION_ALREADY_STOPPED(HttpStatus.CONFLICT, "이미 위치 수신이 종료되었습니다."),
    LOCATION_NOT_REALTIME_ALERT(HttpStatus.CONFLICT, "실시간 위치 알림이 아닙니다."),
    LOCATION_NOT_FOUND(HttpStatus.NOT_FOUND, "위치 정보를 찾을 수 없습니다."),
    LOCATION_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "위치 권한이 필요합니다."),
    LOCATION_INVALID_COORDINATE(HttpStatus.BAD_REQUEST, "올바르지 않은 좌표입니다."),
    LOCATION_REQUIRED(HttpStatus.BAD_REQUEST, "위치 정보가 필요합니다.");

    private final HttpStatus status;
    private final String message;
}