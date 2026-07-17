package com.gyeote.gyeote_api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponse(
        @Schema(description = "액세스 토큰 (API 호출 시 Authorization 헤더에 Bearer로 사용)")
        String accessToken,

        @Schema(description = "리프레시 토큰 (액세스 토큰 재발급 시 사용)")
        String refreshToken
) {
}
