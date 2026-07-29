package com.gyeote.gyeote_api.domain.emergency.geocoding;

import java.math.BigDecimal;
import java.util.Optional;

public interface ReverseGeocodingService {
    Optional<String> findAddress(BigDecimal latitude, BigDecimal longitude);
}
