package com.gyeote.gyeote_api.domain.auth.controller;

import com.gyeote.gyeote_api.domain.auth.dto.*;
import com.gyeote.gyeote_api.domain.auth.service.AuthService;
import com.gyeote.gyeote_api.domain.auth.service.SignupService;
import com.gyeote.gyeote_api.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 관련 API")
public class AuthController {

    private final AuthService authService;
    private final SignupService signupService;

    @Operation(
            summary = "회원가입",
            description = "아이디, 비밀번호, 핸드폰번호를 통해서 회원가입을 진행합니다."
    )
    @PostMapping("/signup")
    public ApiResponse<Void> signup(@Valid @RequestBody SignupRequest request) {
        signupService.signup(request);
        return ApiResponse.success("회원가입이 완료되었습니다.");
    }

    @Operation(
            summary = "로그인",
            description = "아이디/비밀번호로 로그인 하여 Access/Refresh 토큰을 발급받습니다."
    )
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("로그인에 성공했습니다.", authService.login(request));
    }

    @Operation(
            summary = "토큰 재발급",
            description = "Access 와 Refresh 토큰을 재발급 합니다."
    )
    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success("토큰이 재발급되었습니다.", authService.refresh(request));
    }

    @Operation(
            summary = "로그아웃",
            description = "사용자를 로그아웃 시킵니다."
    )
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @Parameter(description = "유저 아이디", example = "1")
            @AuthenticationPrincipal Long userId
    ) {
        authService.logout(userId);
        return ApiResponse.success("로그아웃되었습니다.");
    }

    @Operation(
            summary = "소셜 로그인",
            description = "카카오톡, 구글, 애플을 통해서 로그인을 진행합니다."
    )
    @PostMapping("/social/login")
    public ApiResponse<SocialLoginResponse> socialLogin(
            @Valid @RequestBody SocialLoginRequest request
    ) {
        SocialLoginResponse response = authService.socialLogin(request);
        String message = response.successMessage();
        return ApiResponse.success(message, response);
    }

    @Operation(
            summary = "소셜 자동 로그인",
            description = "소셜 로그인시 이미 회원가입되어 있는 사용자면 바로 로그인을 진행합니다."
    )
    @PostMapping("/social/signup/complete")
    public ApiResponse<SocialSignupCompleteResponse> completeSocialSignup(
            @Valid @RequestBody SocialSignupCompleteRequest request
    ) {
        return ApiResponse.success("소셜 계정 설정이 완료되었습니다.", signupService.completeSocialSignup(request));
    }

}
