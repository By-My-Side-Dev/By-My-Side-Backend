package com.gyeote.gyeote_api.domain.user.controller;

import com.gyeote.gyeote_api.domain.user.dto.*;
import com.gyeote.gyeote_api.domain.user.service.UserService;
import com.gyeote.gyeote_api.global.auth.UserPrincipal;
import com.gyeote.gyeote_api.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Tag(name = "User", description = "유저 관련 API")
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "내 정보 조회",
            description = "유저의 전화번호, 아이디를 조회할 수 있도록 합니다."
    )
    @GetMapping("/profile")
    public ApiResponse<UserProfileResponse> getProfile(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success("내 정보 조회에 성공했습니다.", userService.getProfile(principal.id()));
    }

    @Operation(
            summary = "실시간 위치 전송 토글",
            description = "내 실시간 위치 정보 토글 형태로 변경하도록 합니다."
    )
    @PutMapping("/settings/realtime-location")
    public ApiResponse<RealtimeLocationSettingResponse> updateRealtimeLocation(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody RealtimeLocationSettingRequest request
    ) {
        return ApiResponse.success("실시간 위치 전송 토글 성공", userService.updateRealtimeLocation(principal.id(), request));
    }

    @Operation(
            summary = "내 정보 수정",
            description = "내 프로필 정보 수정합니다."
    )
    @PatchMapping("/profile")
    public ApiResponse<UserProfileUpdateResponse> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UserProfileUpdateRequest request
            ) {
        return ApiResponse.success("내 정보 업데이트를 성공했습니다.", userService.updateProfile(principal.id(), request));
    }

    @Operation(
            summary = "회원 탈퇴",
            description = "회원 정보를 직접 삭제하지 않고 삭제 시간을 DB에 저장합니다."
    )
    @DeleteMapping
    public ApiResponse<Void> deleteUser(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        userService.deleteUser(principal.id());
        return ApiResponse.success("회원 탈퇴를 성공했습니다.", null);
    }

}
