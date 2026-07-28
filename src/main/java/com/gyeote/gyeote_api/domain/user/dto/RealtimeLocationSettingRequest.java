package com.gyeote.gyeote_api.domain.user.dto;

public record RealtimeLocationSettingRequest(
        boolean realtimeLocationEnable
) {
    public static RealtimeLocationSettingRequest of(
            boolean realtimeLocationEnable
    ) {
        return new RealtimeLocationSettingRequest(
                realtimeLocationEnable
        );
    }
}
