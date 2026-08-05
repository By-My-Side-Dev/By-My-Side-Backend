package com.gyeote.gyeote_api.domain.emergency.service;

import com.gyeote.gyeote_api.domain.emergency.dto.RealtimeLocationUpdateRequest;
import com.gyeote.gyeote_api.domain.emergency.entity.AlertLocations;
import com.gyeote.gyeote_api.domain.emergency.entity.EmergencyAlerts;
import com.gyeote.gyeote_api.domain.emergency.geocoding.ReverseGeocodingService;
import com.gyeote.gyeote_api.domain.emergency.repository.AlertLocationsRepository;
import com.gyeote.gyeote_api.domain.emergency.repository.EmergencyAlertsRepository;
import com.gyeote.gyeote_api.domain.user.entity.User;
import com.gyeote.gyeote_api.domain.user.repository.UserRepository;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import com.gyeote.gyeote_api.global.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmergencyServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private EmergencyAlertsRepository emergencyAlertsRepository;
    @Mock
    private AlertLocationsRepository alertLocationsRepository;
    @Mock
    private ReverseGeocodingService reverseGeocodingService;

    private EmergencyService emergencyService;
    private EmergencyAlerts emergencyAlert;

    @BeforeEach
    void setUp() {
        emergencyService = new EmergencyService(
                userRepository,
                emergencyAlertsRepository,
                alertLocationsRepository,
                reverseGeocodingService
        );
        ReflectionTestUtils.setField(emergencyService, "locationUpdateIntervalSeconds", 15L);

        emergencyAlert = EmergencyAlerts.builder()
                .sender(User.builder().build())
                .latitude(new BigDecimal("37.1234567"))
                .longitude(new BigDecimal("127.1234567"))
                .isRealtime(true)
                .build();
    }

    @Test
    void rejectsLocationUpdateWithinConfiguredInterval() {
        AlertLocations latestLocation = locationUpdatedAt(LocalDateTime.now().minusSeconds(5));
        when(emergencyAlertsRepository.findByIdAndSenderIdForUpdate(1L, 10L))
                .thenReturn(Optional.of(emergencyAlert));
        when(alertLocationsRepository.findFirstByEmergencyAlert_IdOrderByUpdatedAtDescIdDesc(1L))
                .thenReturn(Optional.of(latestLocation));

        assertThatThrownBy(() -> emergencyService.updateRealtimeLocation(10L, 1L, request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_TOO_MANY_REQUESTS);

        verify(alertLocationsRepository, never()).save(any());
    }

    @Test
    void acceptsLocationUpdateAfterConfiguredInterval() {
        AlertLocations latestLocation = locationUpdatedAt(LocalDateTime.now().minusSeconds(16));
        when(emergencyAlertsRepository.findByIdAndSenderIdForUpdate(1L, 10L))
                .thenReturn(Optional.of(emergencyAlert));
        when(alertLocationsRepository.findFirstByEmergencyAlert_IdOrderByUpdatedAtDescIdDesc(1L))
                .thenReturn(Optional.of(latestLocation));

        emergencyService.updateRealtimeLocation(10L, 1L, request());

        verify(alertLocationsRepository).save(any(AlertLocations.class));
    }

    private AlertLocations locationUpdatedAt(LocalDateTime updatedAt) {
        AlertLocations location = AlertLocations.builder()
                .emergencyAlert(emergencyAlert)
                .latitude(new BigDecimal("37.1234567"))
                .longitude(new BigDecimal("127.1234567"))
                .build();
        ReflectionTestUtils.setField(location, "updatedAt", updatedAt);
        return location;
    }

    private RealtimeLocationUpdateRequest request() {
        return new RealtimeLocationUpdateRequest(
                new BigDecimal("37.7654321"),
                new BigDecimal("127.7654321")
        );
    }
}
