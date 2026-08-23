package com.gyeote.gyeote_api.domain.alert.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReceivedAlertDetailResponse(
        Long alertRecipientId,
        Long alertId,
        Long senderId,
        String senderName,
        String senderProfileImageUrl,
        BigDecimal latitude,
        BigDecimal longitude,
        String address,
        boolean realtime,
        LocalDateTime realtimeStoppedAt,
        LocalDateTime endedAt,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime locationUpdatedAt
) {
}
