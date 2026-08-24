package com.gyeote.gyeote_api.domain.alert.controller;

import com.gyeote.gyeote_api.domain.alert.dto.AlertHistoryResponse;
import com.gyeote.gyeote_api.domain.alert.dto.ReceivedAlertDetailResponse;
import com.gyeote.gyeote_api.domain.alert.dto.ReceivedAlertListResponse;
import com.gyeote.gyeote_api.domain.alert.service.AlertService;
import com.gyeote.gyeote_api.global.auth.UserPrincipal;
import com.gyeote.gyeote_api.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Validated
@Tag(name = "Received Alert", description = "수신 긴급 알림 API")
public class AlertController {

    private final AlertService alertService;

    @GetMapping("/received")
    @Operation(
            summary = "수신 알림 목록 조회",
            description = "내가 받은 24시간 내 긴급 알림 목록을 최신순으로 조회합니다."
    )
    public ApiResponse<ReceivedAlertListResponse> getReceivedAlerts(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success("수신 알림 목록 조회 성공", alertService.getReceivedAlerts(principal.id()));
    }

    @GetMapping("/received/{alertId}")
    @Operation(
            summary = "수신 알림 상세 조회",
            description = "목록에서 받은 alertId로 특정 수신 알림의 발신자, 최신 위치와 시간 정보를 조회합니다."
    )
    public ApiResponse<ReceivedAlertDetailResponse> getReceivedAlert(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long alertId
    ) {
        return ApiResponse.success("수신 알림 상세를 조회했습니다.", alertService.getReceivedAlert(principal.id(), alertId));
    }

    @GetMapping("/history")
    @Operation(
            summary = "이전 알림 이력 조회",
            description = "수신 후 24시간이 지나 이전 이력으로 이동한 알림을 조회합니다."
    )
    public ApiResponse<List<AlertHistoryResponse>> getAlertHistory(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success("이전 알림 이력을 조회했습니다.", alertService.getAlertHistory(principal.id()));
    }
}
