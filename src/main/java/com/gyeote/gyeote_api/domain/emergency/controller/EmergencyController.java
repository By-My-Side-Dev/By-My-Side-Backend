package com.gyeote.gyeote_api.domain.emergency.controller;

import com.gyeote.gyeote_api.domain.emergency.dto.CurrentEmergencyStatusResponse;
import com.gyeote.gyeote_api.domain.emergency.dto.EmergencyRequest;
import com.gyeote.gyeote_api.domain.emergency.dto.EmergencyResponse;
import com.gyeote.gyeote_api.domain.emergency.dto.LocationUpdateRequest;
import com.gyeote.gyeote_api.domain.emergency.dto.LocationUpdateResponse;
import com.gyeote.gyeote_api.domain.emergency.service.EmergencyService;
import com.gyeote.gyeote_api.global.auth.UserPrincipal;
import com.gyeote.gyeote_api.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/emergency-alerts")
@RequiredArgsConstructor
@Tag(name = "Emergency", description = "긴급 호출 관련 API")
public class EmergencyController {

    private final EmergencyService emergencyService;
    private final SimpMessagingTemplate messagingTemplate;

    @Operation(
            summary = "현재 긴급 호출 상태 조회",
            description = """
                    발신자 본인의 가장 최근 긴급 호출 상태와 백그라운드 위치 전송 필요 여부를 조회합니다.
                    진행 중인 호출이 없거나 최신 호출이 수동 종료·24시간 경과로 만료된 경우에도 200 OK로 isActive=false, shouldSendLocation=false를 반환합니다.
                    shouldSendLocation=true는 조회 시점 기준 안내 값이므로, 직후 소켓 전송 등이 먼저 도착하면 후속 위치 저장 요청에서 429가 발생할 수 있습니다.
                    응답의 lastLocationUpdatedAt 및 nextLocationUpdateAt 시각은 서버 표준 시간대(Asia/Seoul, UTC+09:00) 기준 ISO-8601 로컬 시각입니다.
                    """
    )
    @GetMapping("/current")
    public ApiResponse<CurrentEmergencyStatusResponse> getCurrentEmergencyStatus(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success("현재 긴급 호출 상태 조회 성공", emergencyService.getCurrentEmergencyStatus(principal.id()));
    }

    @Operation(
            summary = "도움 요청 생성",
            description = """
                    사용자가 도움 요청 버튼을 누르면 긴급 호출을 생성합니다.
                    요청 좌표는 호출 시점 위치로 저장되며 소수점 7자리로 반올림됩니다.
                    주소는 서버가 좌표를 역지오코딩하여 저장하며, 조회할 수 없는 경우 null로 저장됩니다.
                    응답에는 알림 ID와 역지오코딩된 주소가 포함됩니다.
                    무료 사용자는 만료되지 않고 종료되지 않은 최근 알림이 있으면 같은 알림의 좌표와 주소만 갱신합니다.
                    이때 기존 수신자와 만료 시각을 유지하고 FCM 알림을 다시 발송하지 않습니다.
                    재사용 가능한 알림이 없거나 유료 사용자인 경우 새 알림과 최초 위치 행을 생성합니다.
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
            summary = "유료 긴급 호출 위치 추가",
            description = """
                    호출 발신자 본인만 사용할 수 있으며, 실시간 위치 공유가 활성화되어 있어야 합니다.
                    발신자의 요금제가 PAID가 아니거나 실시간 위치 공유가 비활성화되어 있으면 403을 반환합니다.
                    위치 이력을 새 행으로 저장하며, 좌표는 소수점 이하 7자리로 반올림됩니다.
                    최소 갱신 간격은 기본 15초이며, 설정된 간격이 지나기 전 요청하면 429를 반환합니다.
                    성공 시 /topic/emergency-alerts/{alertId}/location으로 저장된 좌표를 브로드캐스트합니다.
                    """
    )
    @PostMapping("/{alertId}/location/paid")
    public ApiResponse<LocationUpdateResponse> createPaidLocation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long alertId,
            @Valid @RequestBody LocationUpdateRequest request
    ) {
        LocationUpdateResponse response = emergencyService.createPaidLocation(principal.id(), alertId, request);
        messagingTemplate.convertAndSend("/topic/emergency-alerts/" + alertId + "/location", response);
        return ApiResponse.success("위치 갱신 성공", response);
    }

    @Operation(
            summary = "무료 긴급 호출 위치 갱신",
            description = """
                    호출 발신자 본인만 사용할 수 있으며, 실시간 위치 공유가 활성화되어 있어야 합니다.
                    발신자의 요금제가 FREE가 아니거나 실시간 위치 공유가 비활성화되어 있으면 403을 반환합니다.
                    최신 위치 행의 좌표를 갱신하며, 위치 행이 없으면 새로 생성합니다.
                    좌표는 소수점 이하 7자리로 반올림됩니다.
                    최소 갱신 간격은 기본 15초이며, 설정된 간격이 지나기 전 요청하면 429를 반환합니다.
                    성공 시 /topic/emergency-alerts/{alertId}/location으로 저장된 좌표를 브로드캐스트합니다.
                    """
    )
    @PostMapping("/{alertId}/location/free")
    public ApiResponse<LocationUpdateResponse> updateFreeLocation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long alertId,
            @Valid @RequestBody LocationUpdateRequest request
    ) {
        LocationUpdateResponse response = emergencyService.updateFreeLocation(principal.id(), alertId, request);
        messagingTemplate.convertAndSend("/topic/emergency-alerts/" + alertId + "/location", response);
        return ApiResponse.success("위치 갱신 성공", response);
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
