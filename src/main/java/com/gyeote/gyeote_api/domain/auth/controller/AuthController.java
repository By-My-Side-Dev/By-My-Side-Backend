package com.gyeote.gyeote_api.domain.auth.controller;

import com.gyeote.gyeote_api.domain.auth.dto.*;
import com.gyeote.gyeote_api.domain.auth.service.AuthService;
import com.gyeote.gyeote_api.domain.auth.service.SignupService;
import com.gyeote.gyeote_api.domain.auth.service.EmailVerificationService;
import com.gyeote.gyeote_api.domain.auth.service.PasswordResetService;
import com.gyeote.gyeote_api.global.auth.UserPrincipal;
import com.gyeote.gyeote_api.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
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
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;

    @Operation(
            summary = "회원가입",
            description = "아이디, 비밀번호, 휴대폰번호와 인증한 이메일 및 emailVerificationToken으로 가입합니다. 인증 토큰은 10분간 한 번만 사용할 수 있습니다. 이메일·아이디 중복은 409입니다."
    )
    @PostMapping("/signup")
    public ApiResponse<Void> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        signupService.signup(request);
        return ApiResponse.success("회원가입이 완료되었습니다.");
    }

    @Operation(summary = "가입 이메일 인증번호 요청",
            description = "미가입 이메일에 6자리 인증번호를 비동기로 발송합니다. 유효기간 5분, 재요청 60초, 이메일당 일일 10회입니다. 중복 이메일은 409, 요청 제한은 429입니다.")
    @PostMapping("/email/signup/code")
    public ApiResponse<Void> signupEmailCode(@Valid @RequestBody SignupEmailCodeRequest request) {
        signupService.requestEmailCode(request.email());
        return ApiResponse.success("인증번호가 발송되었습니다.");
    }

    @Operation(summary = "가입 이메일 인증번호 검증",
            description = "5회 불일치 시 인증번호가 폐기됩니다. 성공 시 가입에 사용할 10분 유효 일회용 토큰을 발급합니다. 만료·불일치는 400입니다.")
    @PostMapping("/email/signup/verify")
    public ApiResponse<EmailVerificationResponse> signupEmailVerify(@Valid @RequestBody SignupEmailVerifyRequest request) {
        return ApiResponse.success("이메일 인증이 완료되었습니다.",
                new EmailVerificationResponse(emailVerificationService.verifySignup(request.email(), request.code())));
    }

    @Operation(summary = "비밀번호 재설정 인증번호 요청",
            description = "활성 일반 계정의 아이디·이메일이 일치할 때 발송하며, 일치 여부와 무관하게 같은 200 응답을 반환합니다. 미일치 요청도 재요청 60초·일일 10회 제한(429)을 적용합니다.")
    @PostMapping("/password-reset/code")
    public ApiResponse<Void> passwordResetCode(@Valid @RequestBody PasswordResetCodeRequest request) {
        passwordResetService.requestCode(request);
        return ApiResponse.success("인증번호가 발송되었습니다.");
    }

    @Operation(summary = "비밀번호 재설정 인증번호 검증",
            description = "아이디·이메일과 6자리 인증번호를 검증하고 10분 유효 일회용 resetToken을 반환합니다. 계정 미일치·인증번호 만료·불일치는 동일한 400 오류입니다.")
    @PostMapping("/password-reset/verify")
    public ApiResponse<PasswordResetTokenResponse> passwordResetVerify(@Valid @RequestBody PasswordResetVerifyRequest request) {
        return ApiResponse.success("인증이 완료되었습니다.", passwordResetService.verify(request));
    }

    @Operation(summary = "새 비밀번호 설정",
            description = "일회용 resetToken을 소모해 비밀번호를 변경하고 기존 Refresh Token을 삭제합니다. 기존 Access Token은 만료 시까지 유효합니다. 무효·만료 토큰은 400입니다.")
    @PostMapping("/password-reset/confirm")
    public ApiResponse<Void> passwordResetConfirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirm(request);
        return ApiResponse.success("비밀번호가 변경되었습니다. 다시 로그인해주세요.");
    }

    @Operation(
            summary = "로그인",
            description = "아이디/비밀번호로 로그인 하여 Access/Refresh 토큰을 발급받습니다."
    )
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ApiResponse.success("로그인에 성공했습니다.", authService.login(request));
    }

    @Operation(
            summary = "토큰 재발급",
            description = "Access 와 Refresh 토큰을 재발급 합니다."
    )
    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> refresh(
            @Valid @RequestBody RefreshRequest request
    ) {
        return ApiResponse.success("토큰이 재발급되었습니다.", authService.refresh(request));
    }

    @Operation(
            summary = "로그아웃",
            description = "사용자를 로그아웃 시킵니다."
    )
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        authService.logout(principal.id());
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
