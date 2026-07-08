package com.gyeote.gyeote_api.domain.auth.social;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GoogleTokenVerifier extends OidcTokenVerifier {

    private static final String ISSUER = "https://accounts.google.com";
    private static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";

    public GoogleTokenVerifier(@Value("${social.oauth.google.client-ids:}") String clientIds) {
        super(ISSUER, JWK_SET_URI, clientIds);
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.GOOGLE;
    }
}
