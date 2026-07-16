package com.gyeote.gyeote_api.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record SocialSignupCompleteRequest(
        @NotBlank
        String onboardingToken,

        @NotBlank
        String name,

        @NotBlank
        String loginId,

        @NotBlank
        String password,

        @NotBlank
        String phone
) {
}
