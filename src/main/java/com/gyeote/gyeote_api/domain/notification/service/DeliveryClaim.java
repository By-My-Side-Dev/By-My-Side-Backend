package com.gyeote.gyeote_api.domain.notification.service;

import java.time.LocalDateTime;

public record DeliveryClaim(
        Long deliveryId,
        Long alertId,
        Long recipientUserId,
        String senderName,
        int attemptCount,
        LocalDateTime createdAt
) {
}
