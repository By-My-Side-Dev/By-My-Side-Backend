package com.gyeote.gyeote_api.domain.emergency.controller;

import com.gyeote.gyeote_api.domain.emergency.dto.EmergencyRequest;
import com.gyeote.gyeote_api.domain.emergency.dto.EmergencyResponse;
import com.gyeote.gyeote_api.domain.emergency.service.EmergencyService;
import com.gyeote.gyeote_api.global.auth.UserPrincipal;
import com.gyeote.gyeote_api.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/emergency-alerts")
@RequiredArgsConstructor
@Tag(name = "Emergency", description = "긴급 호출 관련 API")
public class EmergencyController {

    private final EmergencyService emergencyService;

    @Operation(
            summary = "도움 요청 생성",
            description = "사용자가 도움 요청 버튼을 누르면 긴급 호출을 생성한다."
    )
    @PostMapping
    public ApiResponse<EmergencyResponse> createEmergency(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody EmergencyRequest request
    ) {
        return ApiResponse.success("도움 요청 전송 성공", emergencyService.createEmergency(principal.id(), request));
    }

    @Operation(
            summary = "도움 요청 종료",
            description = "진행 중인 긴급 호출을 종료 한다."
    )
    @PostMapping("/{alertId}/end")
    public ApiResponse<Void> endEmergency(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long alertId
    ) {
        emergencyService.endEmergency(principal.id(), alertId);
        return ApiResponse.success("위치 수신 종료");
    }
}
