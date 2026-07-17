package com.gyeote.gyeote_api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SocialSignupCompleteResponse(
        @Schema(description = "소셜 회원가입 완료 후 발급되는 토큰")
        TokenResponse tokens
) {
}
