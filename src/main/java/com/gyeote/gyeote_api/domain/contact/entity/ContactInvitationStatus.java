package com.gyeote.gyeote_api.domain.contact.entity;

public enum ContactInvitationStatus {
    PENDING,  // 초대를 보낸 후 상대방의 응답을 기다리는 상태
    ACCEPTED, // 초대를 받은 사용자가 수락한 상태
    REJECTED, // 초대를 받은 사용자가 거절한 상태
    CANCELED  // 초대를 보낸 사용자가 응답 전에 취소한 상태
}
