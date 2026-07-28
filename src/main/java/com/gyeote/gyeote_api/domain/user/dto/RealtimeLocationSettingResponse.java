package com.gyeote.gyeote_api.domain.user.dto;

public record RealtimeLocationSettingResponse(
        boolean realtimeLocationEnable
) {
    public static RealtimeLocationSettingResponse of(
            boolean realtimeLocationEnable
    ) {
        return new RealtimeLocationSettingResponse(
                realtimeLocationEnable
        );
    }
}
