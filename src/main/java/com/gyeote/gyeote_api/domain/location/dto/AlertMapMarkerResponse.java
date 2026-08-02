package com.gyeote.gyeote_api.domain.location.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 전체 지도에 표시할 수신 알림 마커 1건.
 *
 * @param realtime      현재 실시간 위치가 갱신되는 마커인지 여부. false면 좌표는 호출 시점 위치다.
 * @param locationUpdatedAt 좌표가 마지막으로 갱신된 시각. 호출 시점 위치면 호출 시각과 같다.
 */
public record AlertMapMarkerResponse(
        Long alertRecipientId,
        Long alertId,
        Long senderId,
        String senderName,
        String senderProfileImageUrl,
        BigDecimal latitude,
        BigDecimal longitude,
        String address,
        boolean realtime,
        boolean read,
        LocalDateTime createdAt,
        LocalDateTime locationUpdatedAt
) {
}