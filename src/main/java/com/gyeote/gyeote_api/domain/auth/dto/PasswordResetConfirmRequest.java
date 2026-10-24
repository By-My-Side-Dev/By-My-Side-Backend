package com.gyeote.gyeote_api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record PasswordResetConfirmRequest(
        @Schema(description = "일회용 재설정 토큰 (10분 유효)")
        @NotBlank @Size(max = 36) String resetToken,
        @Schema(description = "8~64자, 영문자와 숫자 포함", format = "password")
        @NotBlank(message = "SIGNUP_REQUIRED_PASSWORD")
        @Size(min = 8, message = "SIGNUP_PASSWORD_TOO_SHORT")
        @Size(max = 64, message = "SIGNUP_PASSWORD_TOO_LONG")
        @Pattern(regexp = ".*[a-zA-Z].*", message = "SIGNUP_PASSWORD_LETTER_REQUIRED")
        @Pattern(regexp = ".*[0-9].*", message = "SIGNUP_PASSWORD_NUMBER_REQUIRED")
        String newPassword
) {
}
