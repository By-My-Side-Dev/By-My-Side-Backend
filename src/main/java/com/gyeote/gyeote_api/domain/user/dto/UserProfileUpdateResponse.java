package com.gyeote.gyeote_api.domain.user.dto;

public record UserProfileUpdateResponse(
        String name,
        String phoneNumber
) {
        public static UserProfileUpdateResponse from(
                String name,
                String phoneNumber
        ) {
            return new UserProfileUpdateResponse(
                    name,
                    phoneNumber
            );
        }
}
