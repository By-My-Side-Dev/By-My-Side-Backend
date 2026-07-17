package com.gyeote.gyeote_api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(description = "로그인 아이디", example = "id1234")
        @NotBlank(message = "AUTH_INVALID_LOGIN_INFO")
        String loginId,

        @Schema(description = "비밀번호", example = "password1234")
        @NotBlank(message = "AUTH_INVALID_LOGIN_INFO")
        String password
) {
}
