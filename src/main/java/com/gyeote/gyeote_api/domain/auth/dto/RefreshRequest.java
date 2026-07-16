package com.gyeote.gyeote_api.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "AUTH_INVALID_REFRESH_TOKEN")
        String refreshToken
) {
}
