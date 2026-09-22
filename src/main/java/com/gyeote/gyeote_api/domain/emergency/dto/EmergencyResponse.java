package com.gyeote.gyeote_api.domain.emergency.dto;

public record EmergencyResponse(
        Long alertId,
        String address
) {
    public static EmergencyResponse from(
            Long alertId,
            String address
    ) {
        return new EmergencyResponse(
                alertId,
                address
        );
    }
}
