package com.gyeote.gyeote_api.domain.emergency.controller;

import com.gyeote.gyeote_api.domain.emergency.dto.RealtimeLocationUpdateRequest;
import com.gyeote.gyeote_api.domain.emergency.dto.RealtimeLocationUpdateResponse;
import com.gyeote.gyeote_api.domain.emergency.service.EmergencyService;
import com.gyeote.gyeote_api.global.auth.UserPrincipal;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import com.gyeote.gyeote_api.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Tag(
        name = "Emergency Realtime Location",
        description = """
                STOMP WebSocket 실시간 위치 통신 API입니다.
                /ws로 연결한 뒤 Authorization: Bearer {accessToken} CONNECT 헤더로 인증합니다.
                """
)
public class EmergencyLocationSocketController {

    private final EmergencyService emergencyService;
    private final SimpMessagingTemplate messagingTemplate;

    @Operation(
            summary = "실시간 위치 갱신(WebSocket)",
            description = """
                    STOMP SEND 목적지: /app/emergency-alerts/{alertId}/location
                    실시간 위치가 활성화된 호출의 발신자만 좌표를 갱신할 수 있습니다.
                    위치 갱신은 기본 15초 간격으로 처리하며, 이보다 빠른 요청은 COMMON_TOO_MANY_REQUESTS 오류를 반환합니다.
                    위치를 수신할 때마다 alert_locations에 새 이력 행을 저장합니다.
                    갱신된 좌표는 /topic/emergency-alerts/{alertId}/location 구독자에게 전달됩니다.
                    비즈니스 오류는 /user/queue/errors에서 수신할 수 있습니다.
                    """
    )
    @MessageMapping("/emergency-alerts/{alertId}/location")
    public void updateLocation(
            @DestinationVariable Long alertId,
            @Valid @Payload RealtimeLocationUpdateRequest request,
            Authentication authentication
    ) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        RealtimeLocationUpdateResponse response = emergencyService.updateRealtimeLocation(
                principal.id(),
                alertId,
                request
        );

        messagingTemplate.convertAndSend(
                "/topic/emergency-alerts/" + alertId + "/location",
                response
        );
    }

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser("/queue/errors")
    public ApiResponse<Void> handleBusinessException(BusinessException exception) {
        return ApiResponse.error(exception.getErrorCode());
    }
}
