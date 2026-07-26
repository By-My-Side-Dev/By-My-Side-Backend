package com.gyeote.gyeote_api.domain.user.controller;

import com.gyeote.gyeote_api.domain.user.dto.RealtimeLocationSettingRequest;
import com.gyeote.gyeote_api.domain.user.dto.UserProfileResponse;
import com.gyeote.gyeote_api.domain.user.service.UserService;
import com.gyeote.gyeote_api.global.auth.UserPrincipal;
import com.gyeote.gyeote_api.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // [유저] 내 정보 조회 -> 추후에 javadoc으로 변경
    @GetMapping("/profile")
    public ApiResponse<UserProfileResponse> getUserProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("내 정보 조회에 성공했습니다.", userService.getUserProfile(principal.id()));
    }

    @PutMapping("/settings/realtime-location")
    public ApiResponse<?> updateRealtimeLocation(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody RealtimeLocationSettingRequest request
    ) {
        return ApiResponse.success("실시간 위치 전송 토글 성공", userService.updateRealtimeLocation(principal.id(), request));
    }
}
