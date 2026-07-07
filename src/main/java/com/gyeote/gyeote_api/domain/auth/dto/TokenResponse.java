package com.gyeote.gyeote_api.domain.auth.dto;

/**
 * @param expiresIn Access Token 유효 시간(초). 클라이언트가 만료 전에 미리 갱신할 수 있도록 내려준다.
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
