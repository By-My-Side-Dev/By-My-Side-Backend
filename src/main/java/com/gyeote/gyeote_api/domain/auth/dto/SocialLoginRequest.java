package com.gyeote.gyeote_api.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record SocialLoginRequest(
        @NotBlank(message = "AUTH_PROVIDER_TOKEN_UNAUTHORIZED")
        String provider,

        @NotBlank(message = "AUTH_PROVIDER_TOKEN_UNAUTHORIZED")
        String providerToken
) {
}
