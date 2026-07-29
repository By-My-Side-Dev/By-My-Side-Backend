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
            description = """
                    사용자가 도움 요청 버튼을 누르면 긴급 호출을 생성합니다.
                    요청 좌표는 호출 시점 위치로 저장되며 소수점 7자리로 반올림됩니다.
                    주소는 서버가 좌표를 역지오코딩하여 저장하며, 조회할 수 없는 경우 null로 저장됩니다.
                    사용자 실시간 위치 설정이 활성화되어 있으면 alert_locations에 최초 위치를 함께 생성합니다.
                    """
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
            description = """
                    진행 중인 긴급 호출의 실시간 위치 공유를 종료합니다.
                    호출을 생성한 사용자만 종료할 수 있으며, 이미 종료된 호출은 409 Conflict를 반환합니다.
                    실시간 위치 공유가 비활성화된 호출에는 종료 시각을 기록하지 않습니다.
                    """
    )
    @PostMapping("/{alertId}/end")
    public ApiResponse<Void> endEmergency(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long alertId
    ) {
        emergencyService.endEmergency(principal.id(), alertId);
        return ApiResponse.success("도움 요청 종료");
    }

}
