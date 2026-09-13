package com.gyeote.gyeote_api.domain.notification.sender;

import lombok.Getter;

@Getter
public class PushDeliveryException extends RuntimeException {
    private final String errorCode;
    private final PushFailureCategory category;

    public PushDeliveryException(String errorCode, PushFailureCategory category, Throwable cause) {
        super(errorCode, cause);
        this.errorCode = errorCode;
        this.category = category;
    }
}
