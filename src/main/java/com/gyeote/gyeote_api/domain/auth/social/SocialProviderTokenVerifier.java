package com.gyeote.gyeote_api.domain.auth.social;

public interface SocialProviderTokenVerifier {

    SocialProvider provider();

    String verify(String providerToken);
}
