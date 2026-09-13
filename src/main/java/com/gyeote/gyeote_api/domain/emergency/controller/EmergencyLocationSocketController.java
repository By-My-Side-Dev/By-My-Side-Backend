package com.gyeote.gyeote_api.domain.emergency.controller;

import com.gyeote.gyeote_api.domain.emergency.dto.LocationUpdateRequest;
import com.gyeote.gyeote_api.domain.emergency.dto.LocationUpdateResponse;
import com.gyeote.gyeote_api.domain.emergency.service.EmergencyService;
import com.gyeote.gyeote_api.global.auth.UserPrincipal;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import com.gyeote.gyeote_api.global.response.ApiResponse;
import com.gyeote.gyeote_api.global.websocket.WebSocketLogSupport;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class EmergencyLocationSocketController {

    private final EmergencyService emergencyService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/emergency-alerts/{alertId}/location/paid")
    public void createPaidLocation(
            @DestinationVariable Long alertId,
            @Valid @Payload LocationUpdateRequest request,
            Authentication authentication
    ) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        LocationUpdateResponse response = emergencyService.createPaidLocation(
                principal.id(),
                alertId,
                request
        );

        messagingTemplate.convertAndSend(
                "/topic/emergency-alerts/" + alertId + "/location",
                response
        );
    }

    @MessageMapping("/emergency-alerts/{alertId}/location/free")
    public void updateFreeLocation(
            @DestinationVariable Long alertId,
            @Valid @Payload LocationUpdateRequest request,
            Authentication authentication
    ) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        LocationUpdateResponse response = emergencyService.updateFreeLocation(
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
    public ApiResponse<Void> handleBusinessException(BusinessException exception, Message<?> message) {
        StompHeaderAccessor accessor = WebSocketLogSupport.accessor(message);
        log.warn(
                "event=stomp_send_failed sessionId={} userId={} command={} destination={} alertId={} reason=business errorCode={}",
                WebSocketLogSupport.sessionId(accessor),
                WebSocketLogSupport.userId(accessor),
                WebSocketLogSupport.command(accessor),
                WebSocketLogSupport.destination(accessor),
                WebSocketLogSupport.alertId(accessor),
                exception.getErrorCode().getCode()
        );
        return ApiResponse.error(exception.getErrorCode());
    }
}
