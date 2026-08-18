package com.gyeote.gyeote_api.domain.alert.dto;

import org.springframework.data.domain.Page;

public record ReceivedAlertPageResponse(
        int pageNumber,
        int pageSize,
        int totalPages,
        long totalElements,
        boolean first,
        boolean last
) {
    public static ReceivedAlertPageResponse from(Page<?> page) {
        return new ReceivedAlertPageResponse(
                page.getNumber(),
                page.getSize(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.isFirst(),
                page.isLast()
        );
    }
}
