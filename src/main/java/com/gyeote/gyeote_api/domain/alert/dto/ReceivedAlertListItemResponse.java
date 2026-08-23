package com.gyeote.gyeote_api.domain.alert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record ReceivedAlertListItemResponse(
        Long alertId,
        String senderName,
        String profileImageUrl,
        String address,
        LocalDateTime requestedAt,
        @JsonProperty("isRealtime") boolean isRealtime,
        LocalDateTime expiresAt
) {
}
