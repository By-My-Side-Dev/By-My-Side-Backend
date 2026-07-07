package com.gyeote.gyeote_api.domain.auth.controller;

import com.gyeote.gyeote_api.domain.auth.dto.LoginRequest;
import com.gyeote.gyeote_api.domain.auth.dto.RefreshRequest;
import com.gyeote.gyeote_api.domain.auth.dto.SignupRequest;
import com.gyeote.gyeote_api.domain.auth.dto.TokenResponse;
import com.gyeote.gyeote_api.domain.auth.service.AuthService;
import com.gyeote.gyeote_api.domain.auth.service.SignupService;
import com.gyeote.gyeote_api.global.response.ApiResponse;
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
public class AuthController {

    private final AuthService authService;
    private final SignupService signupService;

    @PostMapping("/signup")
    public ApiResponse<Void> signup(@Valid @RequestBody SignupRequest request) {
        signupService.signup(request);
        return ApiResponse.success("회원가입이 완료되었습니다.");
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("로그인에 성공했습니다.", authService.login(request));
    }

    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success("토큰이 재발급되었습니다.", authService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Long userId) {
        authService.logout(userId);
        return ApiResponse.success("로그아웃되었습니다.");
    }
}
