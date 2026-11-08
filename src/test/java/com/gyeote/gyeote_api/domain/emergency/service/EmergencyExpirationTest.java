package com.gyeote.gyeote_api.domain.emergency.service;

import com.gyeote.gyeote_api.domain.alert.repository.AlertRecipientRepository;
import com.gyeote.gyeote_api.domain.alert.scheduler.AlertRecipientCleanupScheduler;
import com.gyeote.gyeote_api.domain.contact.repository.ContactRepository;
import com.gyeote.gyeote_api.domain.emergency.dto.LocationUpdateRequest;
import com.gyeote.gyeote_api.domain.emergency.entity.EmergencyAlerts;
import com.gyeote.gyeote_api.domain.emergency.geocoding.ReverseGeocodingService;
import com.gyeote.gyeote_api.domain.emergency.repository.AlertLocationsRepository;
import com.gyeote.gyeote_api.domain.emergency.repository.EmergencyAlertsRepository;
import com.gyeote.gyeote_api.domain.emergency.scheduler.AlertLocationCleanupScheduler;
import com.gyeote.gyeote_api.domain.notification.service.FcmOutboxService;
import com.gyeote.gyeote_api.domain.user.entity.User;
import com.gyeote.gyeote_api.domain.user.repository.UserRepository;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import com.gyeote.gyeote_api.global.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmergencyExpirationTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private EmergencyAlertsRepository emergencyAlertsRepository;
    @Mock
    private AlertLocationsRepository alertLocationsRepository;
    @Mock
    private AlertRecipientRepository alertRecipientRepository;
    @Mock
    private ContactRepository contactRepository;
    @Mock
    private ReverseGeocodingService reverseGeocodingService;
    @Mock
    private FcmOutboxService fcmOutboxService;

    @Test
    void resolveEndedAtReturnsCreatedAtPlus24HoursWhenExpiredAndNotManuallyEnded() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 5, 12, 0, 0);
        EmergencyAlerts alert = createAlert(1L, createdAt);

        LocalDateTime beforeExpiry = createdAt.plusHours(24).minusSeconds(1);
        assertFalse(alert.isEnded(beforeExpiry));
        assertNull(alert.resolveEndedAt(beforeExpiry));

        LocalDateTime atExpiry = createdAt.plusHours(24);
        assertTrue(alert.isEnded(atExpiry));
        assertEquals(createdAt.plusHours(24), alert.resolveEndedAt(atExpiry));

        LocalDateTime afterExpiry = createdAt.plusHours(25);
        assertTrue(alert.isEnded(afterExpiry));
        assertEquals(createdAt.plusHours(24), alert.resolveEndedAt(afterExpiry));
    }

    @Test
    void resolveEndedAtPreservesManualEndTimeWhenEndedWithin24Hours() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 5, 12, 0, 0);
        LocalDateTime manualEndedAt = createdAt.plusHours(2);
        EmergencyAlerts alert = createAlert(1L, createdAt);
        alert.end(manualEndedAt);

        assertTrue(alert.isEnded(createdAt.plusHours(3)));
        assertEquals(manualEndedAt, alert.resolveEndedAt(createdAt.plusHours(3)));
        assertEquals(manualEndedAt, alert.resolveEndedAt(createdAt.plusHours(30)));
    }

    @Test
    void locationUpdateAndEndEmergencyRejectExpiredAlertEvenBeforeSchedulerRuns() {
        EmergencyService emergencyService = new EmergencyService(
                userRepository,
                emergencyAlertsRepository,
                alertLocationsRepository,
                alertRecipientRepository,
                contactRepository,
                reverseGeocodingService,
                fcmOutboxService,
                mock(org.springframework.transaction.PlatformTransactionManager.class)
        );

        Long userId = 10L;
        Long alertId = 100L;
        EmergencyAlerts expiredAlert = createAlert(userId, LocalDateTime.now().minusHours(24).minusMinutes(1));

        when(emergencyAlertsRepository.findByIdAndSenderIdForUpdate(alertId, userId))
                .thenReturn(Optional.of(expiredAlert));
        when(emergencyAlertsRepository.findByIdAndSenderId(alertId, userId))
                .thenReturn(Optional.of(expiredAlert));

        LocationUpdateRequest locationRequest = new LocationUpdateRequest(
                new BigDecimal("37.5665350"),
                new BigDecimal("126.9779692")
        );

        BusinessException paidException = assertThrows(
                BusinessException.class,
                () -> emergencyService.createPaidLocation(userId, alertId, locationRequest)
        );
        assertEquals(CommonErrorCode.COMMON_CONFLICT, paidException.getErrorCode());

        BusinessException endException = assertThrows(
                BusinessException.class,
                () -> emergencyService.endEmergency(userId, alertId)
        );
        assertEquals(CommonErrorCode.COMMON_CONFLICT, endException.getErrorCode());
    }

    @Test
    void cleanupSchedulersUpdateEndedAtForExpiredAlerts() {
        AlertLocationCleanupScheduler locationScheduler = new AlertLocationCleanupScheduler(
                alertLocationsRepository,
                emergencyAlertsRepository
        );
        locationScheduler.deleteExpiredLocations();
        verify(emergencyAlertsRepository).endExpiredAlerts(any(LocalDateTime.class));

        AlertRecipientCleanupScheduler recipientScheduler = new AlertRecipientCleanupScheduler(
                alertRecipientRepository,
                emergencyAlertsRepository
        );
        recipientScheduler.archiveAndDeleteExpiredRecipients();
        verify(emergencyAlertsRepository, org.mockito.Mockito.times(2)).endExpiredAlerts(any(LocalDateTime.class));
    }

    private EmergencyAlerts createAlert(Long senderId, LocalDateTime createdAt) {
        User sender = mock(User.class);
        org.mockito.Mockito.lenient().when(sender.getId()).thenReturn(senderId);

        EmergencyAlerts alert = EmergencyAlerts.builder()
                .sender(sender)
                .latitude(new BigDecimal("37.5665350"))
                .longitude(new BigDecimal("126.9779692"))
                .address("서울특별시 중구")
                .build();
        ReflectionTestUtils.setField(alert, "createdAt", createdAt);
        return alert;
    }
}
