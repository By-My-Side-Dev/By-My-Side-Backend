package com.gyeote.gyeote_api.domain.contact.exception;

import com.gyeote.gyeote_api.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ContactErrorCode implements ErrorCode {
    CONTACT_ALREADY_DISCONNECTED(HttpStatus.CONFLICT, "이미 연결 해제된 지인입니다."),
    CONTACT_NOT_FOUND(HttpStatus.NOT_FOUND, "지인 관계를 찾을 수 없습니다."),
    CONTACT_INVITATION_ALREADY_CANCELED(HttpStatus.CONFLICT, "이미 취소된 초대입니다."),
    CONTACT_INVITATION_ALREADY_REJECTED(HttpStatus.CONFLICT, "이미 거절된 초대입니다."),
    CONTACT_INVITATION_ALREADY_ACCEPTED(HttpStatus.CONFLICT, "이미 수락된 초대입니다."),
    CONTACT_INVITATION_NOT_SENDER(HttpStatus.FORBIDDEN, "해당 초대를 취소할 권한이 없습니다."),
    CONTACT_INVITATION_NOT_RECEIVER(HttpStatus.FORBIDDEN, "해당 초대에 응답할 권한이 없습니다."),
    CONTACT_INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "지인 초대를 찾을 수 없습니다."),
    CONTACT_INVITATION_ALREADY_RECEIVED(HttpStatus.CONFLICT, "이미 받은 초대가 있습니다."),
    CONTACT_INVITATION_ALREADY_SENT(HttpStatus.CONFLICT, "이미 보낸 초대가 있습니다."),
    CONTACT_ALREADY_CONNECTED(HttpStatus.CONFLICT, "이미 연결된 지인입니다."),
    CONTACT_CANNOT_INVITE_SELF(HttpStatus.BAD_REQUEST, "자기 자신에게 초대할 수 없습니다."),
    CONTACT_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "지인으로 연결할 사용자를 찾을 수 없습니다."),
    BLOCK_TARGET_BLOCKED(HttpStatus.FORBIDDEN, "차단한 사용자에게 요청할 수 없습니다."),
    BLOCKED_BY_TARGET(HttpStatus.FORBIDDEN, "상대방에게 차단되어 요청할 수 없습니다."),
    BLOCK_ALREADY_UNBLOCKED(HttpStatus.CONFLICT, "이미 차단 해제된 사용자입니다."),
    BLOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "차단 정보를 찾을 수 없습니다."),
    BLOCK_ALREADY_BLOCKED(HttpStatus.CONFLICT, "이미 차단한 사용자입니다."),
    BLOCK_CANNOT_BLOCK_SELF(HttpStatus.BAD_REQUEST, "자기 자신을 차단할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
