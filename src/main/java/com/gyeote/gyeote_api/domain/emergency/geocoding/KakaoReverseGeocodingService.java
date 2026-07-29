package com.gyeote.gyeote_api.domain.emergency.geocoding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class KakaoReverseGeocodingService implements ReverseGeocodingService {

    private static final int MAX_ADDRESS_LENGTH = 255;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);

    private final RestClient restClient;
    private final boolean enabled;

    public KakaoReverseGeocodingService(
            RestClient.Builder restClientBuilder,
            @Value("${reverse-geocoding.kakao.base-url}") String baseUrl,
            @Value("${reverse-geocoding.kakao.rest-api-key:}") String restApiKey
    ) {
        this.enabled = StringUtils.hasText(restApiKey);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(REQUEST_TIMEOUT);

        RestClient.Builder kakaoClientBuilder = restClientBuilder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory);

        if (enabled) {
            kakaoClientBuilder.defaultHeader("Authorization", "KakaoAK " + restApiKey);
        } else {
            log.warn("REST API 키가 누락되어 카카오 역지오코딩이 비활성화되었습니다.");
        }

        this.restClient = kakaoClientBuilder.build();
    }

    @Override
    public Optional<String> findAddress(BigDecimal latitude, BigDecimal longitude) {
        if (!enabled) {
            return Optional.empty();
        }

        try {
            KakaoCoordinateAddressResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/geo/coord2address.json")
                            .queryParam("x", longitude.toPlainString())
                            .queryParam("y", latitude.toPlainString())
                            .queryParam("input_coord", "WGS84")
                            .build())
                    .retrieve()
                    .body(KakaoCoordinateAddressResponse.class);

            return extractAddress(response).map(this::limitLength);
        } catch (RestClientException exception) {
            log.warn("카카오 역지오코딩 실패: exception={}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<String> extractAddress(KakaoCoordinateAddressResponse response) {
        if (response == null || response.documents() == null || response.documents().isEmpty()) {
            return Optional.empty();
        }

        KakaoAddressDocument document = response.documents().get(0);
        String roadAddress = addressName(document.roadAddress());

        if (StringUtils.hasText(roadAddress)) {
            return Optional.of(roadAddress);
        }

        return Optional.ofNullable(addressName(document.address()))
                .filter(StringUtils::hasText);
    }

    private String addressName(KakaoAddress address) {
        return address == null ? null : address.addressName();
    }

    private String limitLength(String address) {
        if (address.length() <= MAX_ADDRESS_LENGTH) {
            return address;
        }
        return address.substring(0, MAX_ADDRESS_LENGTH);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoCoordinateAddressResponse(
            List<KakaoAddressDocument> documents
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoAddressDocument(
            @JsonProperty("road_address") KakaoAddress roadAddress,
            KakaoAddress address
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoAddress(
            @JsonProperty("address_name") String addressName
    ) {
    }
}
