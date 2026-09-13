package com.gyeote.gyeote_api.domain.notification.service;

/**
 * FCM 발송 시점에 조회한 디바이스 토큰 정보입니다.
 *
 * @param token 복호화된 FCM 등록 토큰
 * @param tokenHash 저장된 토큰의 변경 여부를 확인하기 위한 해시값
 */
public record DeviceTokenSnapshot(
        String token,
        String tokenHash
) {
}
