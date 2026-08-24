package com.gyeote.gyeote_api.domain.emergency.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 위치 갱신 요청.
 * WebSocket 실시간 전송과 REST 단건 갱신에서 함께 사용한다.
 */
public record LocationUpdateRequest(
        @NotNull
        @DecimalMin("-90")
        @DecimalMax("90")
        BigDecimal latitude,

        @NotNull
        @DecimalMin("-180")
        @DecimalMax("180")
        BigDecimal longitude
) {
}
