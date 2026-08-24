package com.gyeote.gyeote_api.domain.emergency.controller;

import com.gyeote.gyeote_api.domain.emergency.dto.LocationUpdateRequest;
import com.gyeote.gyeote_api.domain.emergency.dto.LocationUpdateResponse;
import com.gyeote.gyeote_api.domain.emergency.service.EmergencyService;
import com.gyeote.gyeote_api.global.auth.UserPrincipal;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import com.gyeote.gyeote_api.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class EmergencyLocationSocketController {

    private final EmergencyService emergencyService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/emergency-alerts/{alertId}/location")
    public void updateLocation(
            @DestinationVariable Long alertId,
            @Valid @Payload LocationUpdateRequest request,
            Authentication authentication
    ) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        LocationUpdateResponse response = emergencyService.updateLocation(
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
