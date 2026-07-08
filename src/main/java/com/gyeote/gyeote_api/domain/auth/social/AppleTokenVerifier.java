package com.gyeote.gyeote_api.domain.auth.social;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppleTokenVerifier extends OidcTokenVerifier {

    private static final String ISSUER = "https://appleid.apple.com";
    private static final String JWK_SET_URI = "https://appleid.apple.com/auth/keys";

    public AppleTokenVerifier(@Value("${social.oauth.apple.client-ids:}") String clientIds) {
        super(ISSUER, JWK_SET_URI, clientIds);
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.APPLE;
    }
}
