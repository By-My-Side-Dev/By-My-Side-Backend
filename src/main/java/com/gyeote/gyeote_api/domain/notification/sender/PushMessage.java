package com.gyeote.gyeote_api.domain.notification.sender;

public record PushMessage(
        Long alertId,
        String senderName,
        String registrationToken
) {
    public String title() {
        return "긴급 도움 요청";
    }

    public String body() {
        return senderName + "님이 긴급 도움을 요청했습니다.";
    }
}
