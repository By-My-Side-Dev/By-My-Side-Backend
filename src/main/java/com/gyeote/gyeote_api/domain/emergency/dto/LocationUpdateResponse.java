package com.gyeote.gyeote_api.domain.emergency.dto;

import java.math.BigDecimal;

/**
 * 위치 갱신 응답.
 * WebSocket 실시간 전송과 REST 단건 갱신에서 함께 사용한다.
 */
public record LocationUpdateResponse(
        Long alertId,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
