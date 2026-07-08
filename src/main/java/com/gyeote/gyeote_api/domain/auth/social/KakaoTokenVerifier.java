package com.gyeote.gyeote_api.domain.auth.social;

import com.gyeote.gyeote_api.domain.auth.exception.AuthErrorCode;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KakaoTokenVerifier implements SocialProviderTokenVerifier {

    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;

    public KakaoTokenVerifier(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public String verify(String providerToken) {
        try {
            KakaoUserInfo response = restClient.get()
                    .uri(USER_INFO_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                    .retrieve()
                    .body(KakaoUserInfo.class);

            if (response == null || response.id() == null) {
                throw new BusinessException(AuthErrorCode.AUTH_PROVIDER_TOKEN_UNAUTHORIZED);
            }
            return response.id().toString();
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BusinessException(AuthErrorCode.AUTH_PROVIDER_TOKEN_UNAUTHORIZED);
        }
    }

    private record KakaoUserInfo(Long id) {
    }
}
