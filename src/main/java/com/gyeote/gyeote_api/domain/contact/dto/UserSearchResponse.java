package com.gyeote.gyeote_api.domain.contact.dto;

public record UserSearchResponse(
        Long userId,
        String name,
        String loginId,
        String phoneNumber,
        String profileImageUrl
) {
}
