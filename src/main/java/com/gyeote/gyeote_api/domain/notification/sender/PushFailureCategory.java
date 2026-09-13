package com.gyeote.gyeote_api.domain.notification.sender;

public enum PushFailureCategory {
    TOKEN_INVALID, // 등록 토큰이 만료되었거나 유효하지 않아 더 이상 사용할 수 없는 오류
    RETRYABLE,     // 일시적인 서버·네트워크 문제로 재시도할 수 있는 오류
    CONFIGURATION, // 인증 정보나 권한 등 FCM 설정을 점검해야 하는 오류
    PERMANENT      // 재시도해도 성공할 가능성이 없어 영구 실패로 처리하는 오류
}
