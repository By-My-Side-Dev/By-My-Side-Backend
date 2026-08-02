package com.gyeote.gyeote_api.domain.location.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 특정 수신 알림의 발신자 위치.
 *
 * @param realtime           현재 실시간 위치가 갱신되는 상태인지 여부. false면 좌표는 호출 시점 위치다.
 * @param realtimeStoppedAt  수신자가 위치 수신을 종료한 시각. 종료하지 않았으면 null
 * @param endedAt            발신자가 실시간 공유를 종료한 시각. 종료하지 않았으면 null
 * @param locationUpdatedAt  좌표가 마지막으로 갱신된 시각. 호출 시점 위치면 호출 시각과 같다.
 */
public record AlertLocationResponse(
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
        LocalDateTime locationUpdatedAt
) {
}