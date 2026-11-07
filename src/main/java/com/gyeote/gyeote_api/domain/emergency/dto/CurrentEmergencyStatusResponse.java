package com.gyeote.gyeote_api.domain.emergency.dto;

import com.gyeote.gyeote_api.domain.user.entity.User;
import com.gyeote.gyeote_api.domain.user.entity.UserPlan;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "현재 긴급 호출 상태 및 백그라운드 위치 전송 필요 여부 응답")
public record CurrentEmergencyStatusResponse(
        @Schema(description = "현재 유효한 최신 긴급 호출 ID (활성 호출이 없으면 null)", nullable = true)
        Long alertId,

        @Schema(description = "현재 유효한(미종료 및 생성 후 24시간 이내) 긴급 호출 존재 여부")
        boolean isActive,

        @Schema(description = "발신자의 실시간 위치 공유 설정 활성화 여부")
        boolean realtimeLocationEnabled,

        @Schema(description = "발신자 요금제 (FREE / PAID)")
        UserPlan plan,

        @Schema(description = "조회 시점 기준 즉시 위치를 전송해야 하는지 여부 (안내 값이며 동시 전송 시 429가 발생할 수 있음)")
        boolean shouldSendLocation,

        @Schema(
                description = "마지막으로 위치가 저장된 시각 (서버 기준 시간대: Asia/Seoul, UTC+09:00)",
                nullable = true
        )
        LocalDateTime lastLocationUpdatedAt,

        @Schema(
                description = "다음 위치 전송이 허용되는 시각 (서버 기준 시간대: Asia/Seoul, UTC+09:00. 활성 호출이 없거나 위치 공유 비활성화, 또는 다음 허용 시점이 호출 만료 시각 이후이면 null)",
                nullable = true
        )
        LocalDateTime nextLocationUpdateAt
) {

    public static CurrentEmergencyStatusResponse inactive(User sender) {
        return new CurrentEmergencyStatusResponse(
                null,
                false,
                sender.isRealtimeLocationEnabled(),
                sender.getPlan(),
                false,
                null,
                null
        );
    }

    public static CurrentEmergencyStatusResponse active(
            Long alertId,
            User sender,
            boolean shouldSendLocation,
            LocalDateTime lastLocationUpdatedAt,
            LocalDateTime nextLocationUpdateAt
    ) {
        return new CurrentEmergencyStatusResponse(
                alertId,
                true,
                sender.isRealtimeLocationEnabled(),
                sender.getPlan(),
                shouldSendLocation,
                lastLocationUpdatedAt,
                nextLocationUpdateAt
        );
    }
}
