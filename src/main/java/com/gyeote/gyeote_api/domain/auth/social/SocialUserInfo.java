package com.gyeote.gyeote_api.domain.auth.social;

public record SocialUserInfo(
        SocialProvider provider,
        String providerUid
) {
}
