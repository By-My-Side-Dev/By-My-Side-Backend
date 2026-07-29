package com.gyeote.gyeote_api.domain.emergency.dto;

public record EmergencyResponse(
    Long alertId
) {
    public static EmergencyResponse from(
            Long alertId
    ) {
        return new EmergencyResponse(
                alertId
        );
    }
}
