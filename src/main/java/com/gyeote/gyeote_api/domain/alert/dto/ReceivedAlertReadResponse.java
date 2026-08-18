package com.gyeote.gyeote_api.domain.alert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record ReceivedAlertReadResponse(
        Long alertId,
        @JsonProperty("isRead") boolean isRead,
        LocalDateTime readAt
) {
}
