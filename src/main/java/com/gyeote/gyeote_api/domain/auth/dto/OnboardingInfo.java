package com.gyeote.gyeote_api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record OnboardingInfo(
        @Schema(description = "소셜 로그인 제공자", example = "KAKAO", allowableValues = {"KAKAO", "GOOGLE", "APPLE"})
        String provider,

        @Schema(description = "소셜 제공자 내 사용자 고유 ID")
        String providerUid
) {
}
