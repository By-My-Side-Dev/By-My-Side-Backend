package com.gyeote.gyeote_api.domain.notification.sender;

public interface PushNotificationSender {
    void send(PushMessage message);
}
