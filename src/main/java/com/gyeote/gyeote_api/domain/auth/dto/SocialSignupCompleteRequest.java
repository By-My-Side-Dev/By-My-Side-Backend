package com.gyeote.gyeote_api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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

        @Schema(description = "비밀번호 (8~64자, 영문자와 숫자 포함)", example = "password1234", format = "password")
        @NotBlank(message = "SIGNUP_REQUIRED_PASSWORD")
        @Size(min = 8, message = "SIGNUP_PASSWORD_TOO_SHORT")
        @Size(max = 64, message = "SIGNUP_PASSWORD_TOO_LONG")
        @Pattern(regexp = ".*[a-zA-Z].*", message = "SIGNUP_PASSWORD_LETTER_REQUIRED")
        @Pattern(regexp = ".*[0-9].*", message = "SIGNUP_PASSWORD_NUMBER_REQUIRED")
        String password,

        @Schema(description = "휴대폰 번호 (- 없이 숫자만)", example = "01012345678")
        @NotBlank
        String phone,

        @Schema(description = "위치정보 수집·이용 동의", example = "true")
        boolean agreeLocation,

        @Schema(description = "개인정보 수집 및 이용 동의", example = "true")
        boolean agreePrivacy,

        @Schema(description = "서비스 이용약관 동의", example = "true")
        boolean agreeTerms,

        @Schema(description = "개인정보 제3자 제공 동의", example = "true")
        boolean agreeThirdParty
) {
}
