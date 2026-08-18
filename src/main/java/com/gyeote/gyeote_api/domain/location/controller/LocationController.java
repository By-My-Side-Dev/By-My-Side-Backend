package com.gyeote.gyeote_api.domain.location.controller;

import com.gyeote.gyeote_api.domain.location.dto.AlertLocationResponse;
import com.gyeote.gyeote_api.domain.location.dto.AlertMapMarkerResponse;
import com.gyeote.gyeote_api.domain.location.dto.AlertPathResponse;
import com.gyeote.gyeote_api.domain.location.service.LocationService;
import com.gyeote.gyeote_api.global.auth.UserPrincipal;
import com.gyeote.gyeote_api.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
@Tag(name = "Location", description = "지도/위치 API")
public class LocationController {

    private final LocationService locationService;

    @Operation(
            summary = "전체 지도 위치 조회",
            description = """
                    지인들이 긴급 버튼을 눌러 내가 받은 알림들의 위치를 지도 마커 목록으로 조회합니다.
                    수신 후 24시간이 지나지 않고 이전 알림 이력으로 이동하지 않은 알림만 반환합니다.
                    실시간 공유가 진행 중인 호출은 최신 좌표를, 그 외에는 호출 시점 좌표를 반환하며
                    realtime 필드로 구분할 수 있습니다.
                    """
    )
    @GetMapping("/received-alerts/map-markers")
    public ApiResponse<List<AlertMapMarkerResponse>> getReceivedAlertMapMarkers(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success("지도 마커 목록을 조회했습니다.",
                locationService.getReceivedAlertMapMarkers(principal.id()));
    }

    @Operation(
            summary = "특정 알림 위치 조회",
            description = """
                    전체 지도 마커에서 선택한 alertId로 해당 지인의 위치 상세를 조회합니다.
                    본인이 받은 알림이 아니면 403 Forbidden을 반환합니다.
                    실시간 공유가 진행 중이면 최신 좌표를, 종료됐거나 실시간 호출이 아니면
                    호출 시점 좌표를 반환합니다.
                    """
    )
    @GetMapping("/received-alerts/{alertId}")
    public ApiResponse<AlertLocationResponse> getReceivedAlertLocation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long alertId
    ) {
        return ApiResponse.success("알림 위치를 조회했습니다.",
                locationService.getReceivedAlertLocation(principal.id(), alertId));
    }

    /**
     * 지도에서 선택한 긴급 알림의 누적 이동 경로를 조회한다.
     * 클라이언트는 전체 지도 마커 응답에 포함된 {@code alertId}를 전달해야 한다.
     *
     * @param principal 인증된 수신자 정보
     * @param alertId 전체 지도 마커에서 선택한 긴급 알림 ID
     * @return 호출 시작부터 현재 또는 종료 시점까지 기록된 위치 목록
     */
    @Operation(
            summary = "특정 알림 이동 경로 조회",
            description = """
                    전체 지도 마커에서 선택한 alertId로 호출 시작부터 현재까지의 이동 경로를 조회합니다.
                    좌표는 recordedAt, locationId 오름차순으로 반환합니다.
                    실시간 위치 이력이 없는 알림은 호출 시점의 최초 좌표 한 건을 반환합니다.
                    본인이 받은 알림이 아니면 403 Forbidden을 반환합니다.
                    """
    )
    @GetMapping("/received-alerts/{alertId}/path")
    public ApiResponse<AlertPathResponse> getReceivedAlertPath(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long alertId
    ) {
        return ApiResponse.success("알림 이동 경로를 조회했습니다.",
                locationService.getReceivedAlertPath(principal.id(), alertId));
    }
}
