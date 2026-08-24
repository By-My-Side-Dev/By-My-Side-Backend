package com.gyeote.gyeote_api.domain.user.dto;

import com.gyeote.gyeote_api.domain.user.entity.UserPlan;

public record UserProfileResponse(
        String name,
        String loginId,
        String phoneNumber,
        String profileImageUrl,
        boolean realtimeLocationEnabled,
        UserPlan plan
) {
    public static UserProfileResponse from(
            String name,
            String loginId,
            String phoneNumber,
            String profileImageUrl,
            boolean realtimeLocationEnabled,
            UserPlan plan
    ) {
        return new UserProfileResponse(
                name,
                loginId,
                phoneNumber,
                profileImageUrl,
                realtimeLocationEnabled,
                plan
        );
    }
}
