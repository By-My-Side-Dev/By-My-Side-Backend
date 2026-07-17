package com.gyeote.gyeote_api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record SocialSignupCompleteRequest(
        @Schema(description = "소셜 로그인 시 발급받은 온보딩 토큰", example = "eyJhbGciOiJIUzI1NiIs...")
        @NotBlank
        String onboardingToken,

        @Schema(description = "사용자 이름", example = "홍길동")
        @NotBlank
        String name,

        @Schema(description = "로그인 아이디", example = "id1234")
        @NotBlank
        String loginId,

        @Schema(description = "비밀번호", example = "password1234", format = "password")
        @NotBlank
        String password,

        @Schema(description = "휴대폰 번호 (- 없이 숫자만)", example = "01012345678")
        @NotBlank
        String phone
) {
}
