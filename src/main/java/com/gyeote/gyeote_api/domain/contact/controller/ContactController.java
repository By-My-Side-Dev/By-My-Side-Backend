package com.gyeote.gyeote_api.domain.contact.controller;

import com.gyeote.gyeote_api.domain.contact.dto.*;
import com.gyeote.gyeote_api.domain.contact.service.ContactService;
import com.gyeote.gyeote_api.global.auth.UserPrincipal;
import com.gyeote.gyeote_api.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Contact", description = "지인 연결 API")
public class ContactController {

    private final ContactService contactService;

    @PostMapping("/api/v1/users/search")
    @Operation(
            summary = "아이디로 유저 조회",
            description = "로그인 아이디를 통해 지인으로 연결할 사용자를 조회합니다."
    )
    public ApiResponse<UserSearchResponse> searchUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UserSearchRequest request
    ) {
        return ApiResponse.success("사용자 조회에 성공했습니다.",
                contactService.searchUser(principal.id(), request));
    }

    @PostMapping("/api/v1/contact-invitations")
    @Operation(
            summary = "지인 초대 보내기",
            description = "조회한 사용자에게 지인 연결 초대를 보냅니다."
    )
    public ApiResponse<ContactInvitationResponse> sendInvitation(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ContactInvitationCreateRequest request
    ) {
        return ApiResponse.success("지인 초대를 보냈습니다.",
                contactService.sendInvitation(principal.id(), request));
    }

    @GetMapping("/api/v1/contact-invitations/received")
    @Operation(
            summary = "받은 초대 목록 조회",
            description = "다른 사용자에게 받은 대기 중인 지인 연결 초대 목록을 조회합니다."
    )
    public ApiResponse<List<ContactInvitationResponse>> getReceivedInvitations(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success("받은 초대 목록을 조회했습니다.",
                contactService.getReceivedInvitations(principal.id()));
    }

    @GetMapping("/api/v1/contact-invitations/sent")
    @Operation(
            summary = "보낸 초대 목록 조회",
            description = "다른 사용자에게 보낸 대기 중인 지인 연결 초대 목록을 조회합니다."
    )
    public ApiResponse<List<ContactInvitationResponse>> getSentInvitations(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success("보낸 초대 목록을 조회했습니다.",
                contactService.getSentInvitations(principal.id()));
    }

    @PostMapping("/api/v1/contact-invitations/{invitationId}/accept")
    @Operation(
            summary = "초대 수락",
            description = "내가 받은 지인 연결 초대를 수락하고 양방향 지인 관계를 생성합니다."
    )
    public ApiResponse<Void> acceptInvitation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long invitationId
    ) {
        contactService.acceptInvitation(principal.id(), invitationId);
        return ApiResponse.success("지인 초대를 수락했습니다.");
    }

    @PostMapping("/api/v1/contact-invitations/{invitationId}/reject")
    @Operation(
            summary = "초대 거절",
            description = "내가 받은 지인 연결 초대를 거절합니다."
    )
    public ApiResponse<Void> rejectInvitation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long invitationId
    ) {
        contactService.rejectInvitation(principal.id(), invitationId);
        return ApiResponse.success("지인 초대를 거절했습니다.");
    }

    @PostMapping("/api/v1/contact-invitations/{invitationId}/cancel")
    @Operation(
            summary = "초대 취소",
            description = "내가 보낸 대기 중인 지인 연결 초대를 취소합니다."
    )
    public ApiResponse<Void> cancelInvitation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long invitationId
    ) {
        contactService.cancelInvitation(principal.id(), invitationId);
        return ApiResponse.success("지인 초대를 취소했습니다.");
    }

    @GetMapping("/api/v1/contacts")
    @Operation(
            summary = "지인 목록 조회",
            description = "현재 연결되어 있는 지인 목록과 실시간 위치 수신 설정을 조회합니다."
    )
    public ApiResponse<List<ContactResponse>> getContacts(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success("지인 목록을 조회했습니다.", contactService.getContacts(principal.id()));
    }

    @DeleteMapping("/api/v1/contacts/{contactId}")
    @Operation(
            summary = "지인 연결 해제",
            description = "선택한 지인과의 양방향 연결을 해제합니다."
    )
    public ApiResponse<Void> disconnect(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long contactId
    ) {
        contactService.disconnect(principal.id(), contactId);
        return ApiResponse.success("지인 연결을 해제했습니다.");
    }

    @PatchMapping("/api/v1/contacts/{contactId}/receive-realtime")
    @Operation(
            summary = "실시간 위치 수신 설정",
            description = "선택한 지인의 긴급 호출 실시간 위치를 수신할지 설정합니다."
    )
    public ApiResponse<ReceiveRealtimeResponse> updateReceiveRealtime(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long contactId,
            @Valid @RequestBody ReceiveRealtimeRequest request
    ) {
        return ApiResponse.success("실시간 위치 수신 설정을 변경했습니다.",
                contactService.updateReceiveRealtime(principal.id(), contactId, request));
    }

    @GetMapping("/api/v1/blocked-users")
    @Operation(
            summary = "차단 목록 조회",
            description = "내가 차단한 사용자 목록을 조회합니다."
    )
    public ApiResponse<List<BlockedUserResponse>> getBlockedUsers(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success("차단 목록을 조회했습니다.", contactService.getBlockedUsers(principal.id()));
    }

    @PostMapping("/api/v1/blocked-users")
    @Operation(
            summary = "사용자 차단",
            description = "사용자를 차단하고 기존 지인 연결과 대기 중인 초대를 정리합니다."
    )
    public ApiResponse<BlockedUserResponse> blockUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BlockedUserCreateRequest request
    ) {
        return ApiResponse.success("사용자를 차단했습니다.", contactService.blockUser(principal.id(), request));
    }

    @DeleteMapping("/api/v1/blocked-users/{blockedUserId}")
    @Operation(
            summary = "차단 해제",
            description = "차단 목록에서 선택한 사용자의 차단을 해제합니다."
    )
    public ApiResponse<Void> unblockUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long blockedUserId
    ) {
        contactService.unblockUser(principal.id(), blockedUserId);
        return ApiResponse.success("사용자 차단을 해제했습니다.");
    }
}
