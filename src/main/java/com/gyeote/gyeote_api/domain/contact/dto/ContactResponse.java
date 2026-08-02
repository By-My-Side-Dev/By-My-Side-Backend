package com.gyeote.gyeote_api.domain.contact.dto;

import java.time.LocalDateTime;

public record ContactResponse(
        Long contactId,
        Long userId,
        String name,
        String loginId,
        String phoneNumber,
        String profileImageUrl,
        boolean receiveRealtime,
        LocalDateTime createdAt
) {
}
