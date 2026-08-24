package com.gyeote.gyeote_api.domain.alert.exception;

import com.gyeote.gyeote_api.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AlertErrorCode implements ErrorCode {
    ALERT_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "긴급 알림 전송에 실패했습니다."),
    ALERT_ALREADY_DELETED(HttpStatus.CONFLICT, "이미 삭제된 알림입니다."),
    ALERT_ALREADY_ARCHIVED(HttpStatus.CONFLICT, "이미 이전 알림 이력으로 이동된 알림입니다."),
    ALERT_ALREADY_EXPIRED(HttpStatus.CONFLICT, "만료된 알림입니다."),
    ALERT_RECIPIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "알림 수신 정보를 찾을 수 없습니다."),
    ALERT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 긴급 알림에 접근할 수 없습니다."),
    ALERT_NOT_FOUND(HttpStatus.NOT_FOUND, "긴급 알림을 찾을 수 없습니다."),
    ALERT_NO_CONTACTS(HttpStatus.BAD_REQUEST, "긴급 알림을 보낼 지인이 없습니다."),
    ALERT_INVALID_LOCATION(HttpStatus.BAD_REQUEST, "올바르지 않은 위치 정보입니다."),
    ALERT_LOCATION_REQUIRED(HttpStatus.BAD_REQUEST, "위치 정보가 필요합니다.");

    private final HttpStatus status;
    private final String message;
}
