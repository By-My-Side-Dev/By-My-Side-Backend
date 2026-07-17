package com.gyeote.gyeote_api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Schema(description = "사용자 이름", example = "홍길동")
        @NotBlank
        @Size(max = 50)
        String name,

        @Schema(description = "로그인 아이디", example = "id1234")
        @NotBlank
        @Size(max = 50)
        String loginId,

        @Schema(description = "비밀번호 (8자 이상)", example = "password1234", format = "password")
        @NotBlank(message = "SIGNUP_REQUIRED_PASSWORD")
        @Size(min = 8, message = "SIGNUP_REQUIRED_PASSWORD")
        String password,

        @Schema(description = "휴대폰 번호 (- 없이 숫자만)", example = "01012345678")
        @NotBlank(message = "SIGNUP_REQUIRED_PHONE")
        String phone
) {
}
