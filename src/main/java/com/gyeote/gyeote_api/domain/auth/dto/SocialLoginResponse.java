package com.gyeote.gyeote_api.domain.auth.dto;

public record SocialLoginResponse(
        boolean signupRequired,
        String onboardingToken,
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
