package com.gyeote.gyeote_api.domain.emergency.dto;

import java.math.BigDecimal;

public record RealtimeLocationUpdateResponse(
        Long alertId,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
