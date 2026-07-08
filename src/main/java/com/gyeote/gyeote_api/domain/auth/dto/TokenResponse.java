package com.gyeote.gyeote_api.domain.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken
) {
}
