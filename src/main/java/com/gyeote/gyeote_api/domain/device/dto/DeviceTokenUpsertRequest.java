package com.gyeote.gyeote_api.domain.device.dto;

import com.gyeote.gyeote_api.domain.device.entity.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeviceTokenUpsertRequest(
        @NotBlank @Size(max = 128) String deviceId,
        @NotBlank @Size(max = 4096) String fcmToken,
        @NotNull DevicePlatform platform,
        @Size(max = 50) String appVersion
) {
}
