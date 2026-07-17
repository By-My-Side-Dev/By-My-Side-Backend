package com.gyeote.gyeote_api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SocialLoginResponse(
        @Schema(description = "추가 회원가입 필요 여부. true면 tokens 대신 onboardingToken을 사용해 회원가입을 이어가야 함")
        boolean signupRequired,

        @Schema(description = "추가 회원가입 진행 시 사용하는 토큰 (signupRequired가 true일 때만 값 존재)")
        String onboardingToken,

        @Schema(description = "로그인 성공 시 발급되는 토큰 (signupRequired가 false일 때만 값 존재)")
        TokenResponse tokens
) {
    public static SocialLoginResponse signupRequired(String onboardingToken) {
        return new SocialLoginResponse(true, onboardingToken, null);
    }

    public static SocialLoginResponse loginSuccess(TokenResponse tokens) {
        return new SocialLoginResponse(false, null, tokens);
    }

    public String successMessage() {
        return signupRequired
                ? "추가 회원가입이 필요합니다."
                : "소셜 로그인이 완료되었습니다.";
    }
}
