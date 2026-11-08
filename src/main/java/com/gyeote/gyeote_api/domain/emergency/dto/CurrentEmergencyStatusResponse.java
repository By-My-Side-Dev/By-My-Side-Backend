package com.gyeote.gyeote_api.domain.emergency.dto;

import com.gyeote.gyeote_api.domain.user.entity.User;
import com.gyeote.gyeote_api.domain.user.entity.UserPlan;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 긴급 호출 상태 및 백그라운드 위치 전송 필요 여부 응답")
public record CurrentEmergencyStatusResponse(
        @Schema(description = "현재 유효한 최신 긴급 호출 ID (활성 호출이 없으면 null)", nullable = true)
        Long alertId,

        @Schema(description = "발신자 요금제 (FREE / PAID)")
        UserPlan plan,

        @Schema(description = "조회 시점 기준 즉시 위치를 전송해야 하는지 여부 (안내 값이며 동시 전송 시 429가 발생할 수 있음)")
        boolean shouldSendLocation
) {

    public static CurrentEmergencyStatusResponse inactive(User sender) {
        return new CurrentEmergencyStatusResponse(
                null,
                sender.getPlan(),
                false
        );
    }

    public static CurrentEmergencyStatusResponse active(
            Long alertId,
            User sender,
            boolean shouldSendLocation
    ) {
        return new CurrentEmergencyStatusResponse(
                alertId,
                sender.getPlan(),
                shouldSendLocation
        );
    }
}

