package com.gyeote.gyeote_api.domain.contact.dto;

import jakarta.validation.constraints.NotNull;

public record ReceiveRealtimeRequest(
        @NotNull
        Boolean receiveRealtime
) {
}
