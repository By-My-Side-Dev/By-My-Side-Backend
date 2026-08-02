package com.gyeote.gyeote_api.domain.location.controller;

import com.gyeote.gyeote_api.domain.location.dto.AlertLocationResponse;
import com.gyeote.gyeote_api.domain.location.dto.AlertMapMarkerResponse;
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
                    내가 받은 긴급 알림들의 위치를 지도 마커 목록으로 조회합니다.
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
                    특정 수신 알림의 발신자 위치를 조회합니다.
                    본인이 받은 알림이 아니면 403 Forbidden을 반환합니다.
                    실시간 공유가 진행 중이면 최신 좌표를, 종료됐거나 실시간 호출이 아니면
                    호출 시점 좌표를 반환합니다.
                    """
    )
    @GetMapping("/received-alerts/{alertRecipientId}")
    public ApiResponse<AlertLocationResponse> getReceivedAlertLocation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long alertRecipientId
    ) {
        return ApiResponse.success("알림 위치를 조회했습니다.",
                locationService.getReceivedAlertLocation(principal.id(), alertRecipientId));
    }
}