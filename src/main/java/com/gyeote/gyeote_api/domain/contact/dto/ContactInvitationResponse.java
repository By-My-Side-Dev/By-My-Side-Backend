package com.gyeote.gyeote_api.domain.contact.dto;

import com.gyeote.gyeote_api.domain.contact.entity.ContactInvitationStatus;

import java.time.LocalDateTime;

public record ContactInvitationResponse(
        Long invitationId,
        Long userId,
        String name,
        String loginId,
        String phoneNumber,
        String profileImageUrl,
        ContactInvitationStatus status,
        LocalDateTime createdAt
) {
}
