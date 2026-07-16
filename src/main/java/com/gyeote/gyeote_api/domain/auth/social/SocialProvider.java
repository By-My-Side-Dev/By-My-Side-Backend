package com.gyeote.gyeote_api.domain.auth.social;

import com.gyeote.gyeote_api.domain.auth.exception.AuthErrorCode;
import com.gyeote.gyeote_api.global.exception.BusinessException;

import java.util.Locale;

public enum SocialProvider {
    KAKAO,
    GOOGLE,
    APPLE;

    public static SocialProvider from(String value) {
        try {
            return SocialProvider.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new BusinessException(AuthErrorCode.AUTH_UNSUPPORTED_SOCIAL_PROVIDER);
        }
    }

    public String storageValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
