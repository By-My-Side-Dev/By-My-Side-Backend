package com.gyeote.gyeote_api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PasswordResetTokenResponse(
        @Schema(description = "새 비밀번호 설정에 사용할 일회용 토큰 (10분 유효)")
        String resetToken
) {
}
