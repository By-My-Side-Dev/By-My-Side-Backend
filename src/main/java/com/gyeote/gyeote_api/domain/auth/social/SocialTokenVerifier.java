package com.gyeote.gyeote_api.domain.auth.social;

import com.gyeote.gyeote_api.domain.auth.exception.AuthErrorCode;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class SocialTokenVerifier {

    private final Map<SocialProvider, SocialProviderTokenVerifier> verifiers;

    public SocialTokenVerifier(List<SocialProviderTokenVerifier> verifiers) {
        this.verifiers = new EnumMap<>(SocialProvider.class);
        for (SocialProviderTokenVerifier verifier : verifiers) {
            this.verifiers.put(verifier.provider(), verifier);
        }
    }

    public SocialUserInfo verify(String providerValue, String providerToken) {
        SocialProvider provider = SocialProvider.from(providerValue);
        SocialProviderTokenVerifier verifier = verifiers.get(provider);
        if (verifier == null) {
            throw new BusinessException(AuthErrorCode.AUTH_UNSUPPORTED_SOCIAL_PROVIDER);
        }

        return new SocialUserInfo(provider, verifier.verify(providerToken));
    }
}
