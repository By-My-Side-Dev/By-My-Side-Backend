package com.gyeote.gyeote_api.domain.notification.entity;

public enum DeliveryStatus {
    PENDING,          // 최초 발송을 기다리는 상태
    PROCESSING,       // 워커가 작업을 선점하여 발송을 처리 중인 상태
    SUCCEEDED,        // FCM이 발송 요청을 정상적으로 접수한 상태
    RETRYABLE_FAILED, // 일시적인 오류로 실패하여 재시도를 기다리는 상태
    PERMANENT_FAILED, // 재시도할 수 없거나 최대 시도 횟수를 초과하여 영구 실패한 상태
    EXPIRED,          // 발송 유효 시간인 30분이 지나 만료된 상태
    NO_REGISTRATION   // 사용자에게 등록된 디바이스 토큰이 없는 상태
}
