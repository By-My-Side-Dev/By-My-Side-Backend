package com.gyeote.gyeote_api.domain.alert.dto;

import java.util.List;

public record ReceivedAlertListResponse(
        List<ReceivedAlertListItemResponse> alerts
) {
}
