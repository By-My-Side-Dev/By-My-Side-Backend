package com.gyeote.gyeote_api.domain.user.dto;

public record UserProfileResponse(
        String name,
        String phoneNumber,
        String profileImageUrl,
        boolean realtimeLocationEnabled
) {
    public static UserProfileResponse from(
            String name,
            String phoneNumber,
            String profileImageUrl,
            boolean realtimeLocationEnabled
    ) {
        return new UserProfileResponse(
                name,
                phoneNumber,
                profileImageUrl,
                realtimeLocationEnabled
        );
    }
}
