package com.gyeote.gyeote_api.domain.notification.entity;

public enum DeliveryStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    RETRYABLE_FAILED,
    PERMANENT_FAILED,
    EXPIRED,
    NO_REGISTRATION
}
