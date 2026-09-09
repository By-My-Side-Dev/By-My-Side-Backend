package com.gyeote.gyeote_api.domain.device.controller;

import com.gyeote.gyeote_api.domain.device.dto.DeviceTokenUpsertRequest;
import com.gyeote.gyeote_api.domain.device.service.DeviceTokenService;
import com.gyeote.gyeote_api.global.auth.UserPrincipal;
import com.gyeote.gyeote_api.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users/me/device-token")
@Tag(name = "Device Token", description = "디바이스 토큰 관련 API")
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    @Operation(
            summary = "디바이스 토큰 등록",
            description = "사용자의 디바이스 토큰을 저장한다."
    )
    @PutMapping
    public ApiResponse<Void> register(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody DeviceTokenUpsertRequest request
    ) {
        deviceTokenService.register(principal.id(), request);
        return ApiResponse.success("디바이스 토큰이 등록되었습니다.");
    }

    @Operation(
            summary = "디바이스 토큰 해제",
            description = "사용자의 디바이스 토큰을 해제한다."
    )
    @DeleteMapping("/{deviceId}")
    public ApiResponse<Void> unregister(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable @Size(max = 128) String deviceId
    ) {
        deviceTokenService.unregister(principal.id(), deviceId);
        return ApiResponse.success("디바이스 토큰이 해제되었습니다.");
    }
}
