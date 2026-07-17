package com.gyeote.gyeote_api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @Schema(description = "재발급에 사용할 기존 Refresh 토큰", example = "eyJhbGciOiJIUzI1NiIs...")
        @NotBlank(message = "AUTH_INVALID_REFRESH_TOKEN")
        String refreshToken
) {
}
