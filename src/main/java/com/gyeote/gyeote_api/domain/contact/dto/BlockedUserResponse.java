package com.gyeote.gyeote_api.domain.contact.dto;

import java.time.LocalDateTime;

public record BlockedUserResponse(
        Long blockedUserId,
        String name,
        String loginId,
        String phoneNumber,
        String profileImageUrl,
        LocalDateTime blockedAt
) {
}
