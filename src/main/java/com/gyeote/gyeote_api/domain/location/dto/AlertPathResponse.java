package com.gyeote.gyeote_api.domain.location.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 긴급 호출의 누적 이동 경로.
 *
 * @param alertId 긴급 알림 ID
 * @param endedAt 호출이 진행 중이면 null, 종료됐으면 종료 시각
 * @param locations 호출 시작부터 시간순으로 정렬된 위치 목록
 */
public record AlertPathResponse(
        Long alertId,
        LocalDateTime endedAt,
        List<AlertPathPointResponse> locations
) {
}
