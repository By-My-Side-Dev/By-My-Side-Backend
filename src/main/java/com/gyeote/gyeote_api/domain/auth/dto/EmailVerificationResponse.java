package com.gyeote.gyeote_api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record EmailVerificationResponse(
        @Schema(description = "가입 시 이메일과 함께 제출할 일회용 토큰 (10분 유효)")
        String emailVerificationToken
) {
}
