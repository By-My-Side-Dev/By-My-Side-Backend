package com.gyeote.gyeote_api.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "AUTH_INVALID_LOGIN_INFO")
        String loginId,

        @NotBlank(message = "AUTH_INVALID_LOGIN_INFO")
        String password
) {
}
