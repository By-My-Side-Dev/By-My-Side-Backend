package com.gyeote.gyeote_api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record SocialLoginRequest(
        @Schema(description = "소셜 로그인 제공자", example = "KAKAO", allowableValues = {"KAKAO", "GOOGLE", "APPLE"})
        @NotBlank(message = "AUTH_PROVIDER_TOKEN_UNAUTHORIZED")
        String provider,

        @Schema(description = "소셜 제공자로부터 발급받은 ID 토큰", example = "eyJhbGciOiJIUzI1NiIs...")
        @NotBlank(message = "AUTH_PROVIDER_TOKEN_UNAUTHORIZED")
        String providerToken
) {
}
