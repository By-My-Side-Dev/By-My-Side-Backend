package com.gyeote.gyeote_api.domain.location.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 긴급 호출 이동 경로의 좌표 한 건.
 *
 * @param locationId alert_locations 식별자. 위치 이력이 없는 비실시간 알림의 최초 좌표는 null
 * @param latitude 위도
 * @param longitude 경도
 * @param recordedAt 해당 좌표가 기록된 시각
 */
public record AlertPathPointResponse(
        Long locationId,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDateTime recordedAt
) {
}
