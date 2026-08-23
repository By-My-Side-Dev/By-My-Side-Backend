package com.gyeote.gyeote_api.domain.alert.dto;

import java.time.LocalDateTime;

public record AlertHistoryResponse(
        Long alertRecipientId,
        Long alertId,
        Long senderId,
        String senderName,
        String senderProfileImageUrl,
        String address,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime archivedAt
) {
}
