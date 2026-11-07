package com.gyeote.gyeote_api.domain.emergency.service;

import com.gyeote.gyeote_api.domain.alert.repository.AlertRecipientRepository;
import com.gyeote.gyeote_api.domain.contact.entity.Contact;
import com.gyeote.gyeote_api.domain.contact.repository.ContactRepository;
import com.gyeote.gyeote_api.domain.emergency.controller.EmergencyController;
import com.gyeote.gyeote_api.domain.emergency.dto.CurrentEmergencyStatusResponse;
import com.gyeote.gyeote_api.domain.emergency.dto.EmergencyRequest;
import com.gyeote.gyeote_api.domain.emergency.dto.EmergencyResponse;
import com.gyeote.gyeote_api.domain.emergency.entity.AlertLocations;
import com.gyeote.gyeote_api.domain.emergency.entity.EmergencyAlerts;
import com.gyeote.gyeote_api.domain.emergency.geocoding.ReverseGeocodingService;
import com.gyeote.gyeote_api.domain.emergency.repository.AlertLocationsRepository;
import com.gyeote.gyeote_api.domain.emergency.repository.EmergencyAlertsRepository;
import com.gyeote.gyeote_api.domain.notification.service.FcmOutboxService;
import com.gyeote.gyeote_api.domain.user.entity.User;
import com.gyeote.gyeote_api.domain.user.entity.UserPlan;
import com.gyeote.gyeote_api.domain.user.entity.UserStatus;
import com.gyeote.gyeote_api.domain.user.repository.UserRepository;
import com.gyeote.gyeote_api.global.auth.UserPrincipal;
import com.gyeote.gyeote_api.global.response.ApiResponse;
import com.gyeote.gyeote_api.global.security.JwtTokenProvider;
import com.gyeote.gyeote_api.global.security.WebSocketJwtChannelInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentEmergencyStatusTest {

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
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private EmergencyService emergencyService;

    @BeforeEach
    void setUp() {
        emergencyService = new EmergencyService(
                userRepository,
                emergencyAlertsRepository,
                alertLocationsRepository,
                alertRecipientRepository,
                contactRepository,
                reverseGeocodingService,
                fcmOutboxService
        );
        ReflectionTestUtils.setField(emergencyService, "locationUpdateIntervalSeconds", 15L);
    }

    @Test
    void returnsInactiveWhenNoAlertExists() {
        Long userId = 1L;
        User sender = mockUser(userId, UserPlan.FREE, true);
        when(userRepository.findUserById(userId)).thenReturn(Optional.of(sender));
        when(emergencyAlertsRepository.findFirstBySender_IdOrderByCreatedAtDescIdDesc(userId))
                .thenReturn(Optional.empty());

        CurrentEmergencyStatusResponse response = emergencyService.getCurrentEmergencyStatus(userId);

        assertNull(response.alertId());
        assertFalse(response.isActive());
        assertTrue(response.realtimeLocationEnabled());
        assertEquals(UserPlan.FREE, response.plan());
        assertFalse(response.shouldSendLocation());
        assertNull(response.lastLocationUpdatedAt());
        assertNull(response.nextLocationUpdateAt());
    }

    @Test
    void returnsInactiveWhenLatestAlertIsManuallyEndedWithoutFallingBackToOlderAlert() {
        Long userId = 1L;
        User sender = mockUser(userId, UserPlan.PAID, true);
        LocalDateTime now = LocalDateTime.now();
        EmergencyAlerts latestEndedAlert = createAlert(200L, sender, now.minusMinutes(10));
        latestEndedAlert.end(now.minusMinutes(1));

        when(userRepository.findUserById(userId)).thenReturn(Optional.of(sender));
        when(emergencyAlertsRepository.findFirstBySender_IdOrderByCreatedAtDescIdDesc(userId))
                .thenReturn(Optional.of(latestEndedAlert));

        CurrentEmergencyStatusResponse response = emergencyService.getCurrentEmergencyStatus(userId);

        assertNull(response.alertId());
        assertFalse(response.isActive());
        assertFalse(response.shouldSendLocation());
        assertNull(response.lastLocationUpdatedAt());
        assertNull(response.nextLocationUpdateAt());
        verify(alertLocationsRepository, never())
                .findFirstByEmergencyAlert_IdOrderByUpdatedAtDescIdDesc(any());
    }

    @Test
    void returnsInactiveWhenLatestAlertHasExpiredAfter24Hours() {
        Long userId = 1L;
        User sender = mockUser(userId, UserPlan.FREE, true);
        EmergencyAlerts expiredAlert = createAlert(100L, sender, LocalDateTime.now().minusHours(24));

        when(userRepository.findUserById(userId)).thenReturn(Optional.of(sender));
        when(emergencyAlertsRepository.findFirstBySender_IdOrderByCreatedAtDescIdDesc(userId))
                .thenReturn(Optional.of(expiredAlert));

        CurrentEmergencyStatusResponse response = emergencyService.getCurrentEmergencyStatus(userId);

        assertNull(response.alertId());
        assertFalse(response.isActive());
        assertFalse(response.shouldSendLocation());
        assertNull(response.lastLocationUpdatedAt());
        assertNull(response.nextLocationUpdateAt());
    }

    @Test
    void returnsActiveButShouldNotSendWhenRealtimeLocationSharingIsDisabled() {
        Long userId = 1L;
        User sender = mockUser(userId, UserPlan.FREE, false);
        LocalDateTime now = LocalDateTime.now();
        EmergencyAlerts activeAlert = createAlert(100L, sender, now.minusMinutes(30));
        LocalDateTime lastUpdatedAt = now.minusMinutes(5);
        AlertLocations latestLocation = createLocation(10L, activeAlert, lastUpdatedAt);

        when(userRepository.findUserById(userId)).thenReturn(Optional.of(sender));
        when(emergencyAlertsRepository.findFirstBySender_IdOrderByCreatedAtDescIdDesc(userId))
                .thenReturn(Optional.of(activeAlert));
        when(alertLocationsRepository.findFirstByEmergencyAlert_IdOrderByUpdatedAtDescIdDesc(100L))
                .thenReturn(Optional.of(latestLocation));

        CurrentEmergencyStatusResponse response = emergencyService.getCurrentEmergencyStatus(userId);

        assertEquals(100L, response.alertId());
        assertTrue(response.isActive());
        assertFalse(response.realtimeLocationEnabled());
        assertFalse(response.shouldSendLocation());
        assertEquals(lastUpdatedAt, response.lastLocationUpdatedAt());
        assertNull(response.nextLocationUpdateAt());
    }

    @Test
    void returnsShouldNotSendAndNextUpdateAtWhenWithinUpdateIntervalAndBeforeExpiry() {
        Long userId = 1L;
        User sender = mockUser(userId, UserPlan.PAID, true);
        LocalDateTime now = LocalDateTime.now();
        EmergencyAlerts activeAlert = createAlert(100L, sender, now.minusHours(1));
        LocalDateTime lastUpdatedAt = now.minusSeconds(5);
        AlertLocations latestLocation = createLocation(10L, activeAlert, lastUpdatedAt);

        when(userRepository.findUserById(userId)).thenReturn(Optional.of(sender));
        when(emergencyAlertsRepository.findFirstBySender_IdOrderByCreatedAtDescIdDesc(userId))
                .thenReturn(Optional.of(activeAlert));
        when(alertLocationsRepository.findFirstByEmergencyAlert_IdOrderByUpdatedAtDescIdDesc(100L))
                .thenReturn(Optional.of(latestLocation));

        CurrentEmergencyStatusResponse response = emergencyService.getCurrentEmergencyStatus(userId);

        assertEquals(100L, response.alertId());
        assertTrue(response.isActive());
        assertFalse(response.shouldSendLocation());
        assertEquals(lastUpdatedAt, response.lastLocationUpdatedAt());
        assertEquals(lastUpdatedAt.plusSeconds(15), response.nextLocationUpdateAt());
    }

    @Test
    void returnsNullNextLocationUpdateAtWhenNextAllowedAtReachesOrExceedsAlertExpiry() {
        Long userId = 1L;
        User sender = mockUser(userId, UserPlan.FREE, true);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime createdAt = now.minusHours(24).plusSeconds(5);
        EmergencyAlerts nearlyExpiredAlert = createAlert(100L, sender, createdAt);
        LocalDateTime lastUpdatedAt = now.minusSeconds(1);
        AlertLocations latestLocation = createLocation(10L, nearlyExpiredAlert, lastUpdatedAt);

        when(userRepository.findUserById(userId)).thenReturn(Optional.of(sender));
        when(emergencyAlertsRepository.findFirstBySender_IdOrderByCreatedAtDescIdDesc(userId))
                .thenReturn(Optional.of(nearlyExpiredAlert));
        when(alertLocationsRepository.findFirstByEmergencyAlert_IdOrderByUpdatedAtDescIdDesc(100L))
                .thenReturn(Optional.of(latestLocation));

        CurrentEmergencyStatusResponse response = emergencyService.getCurrentEmergencyStatus(userId);

        assertEquals(100L, response.alertId());
        assertTrue(response.isActive());
        assertFalse(response.shouldSendLocation());
        assertEquals(lastUpdatedAt, response.lastLocationUpdatedAt());
        assertNull(response.nextLocationUpdateAt());
    }

    @Test
    void returnsShouldSendLocationWhenIntervalHasElapsedOrReachedBoundary() {
        Long userId = 1L;
        User sender = mockUser(userId, UserPlan.FREE, true);
        LocalDateTime now = LocalDateTime.now();
        EmergencyAlerts activeAlert = createAlert(100L, sender, now.minusHours(2));
        LocalDateTime lastUpdatedAt = now.minusSeconds(15);
        AlertLocations latestLocation = createLocation(10L, activeAlert, lastUpdatedAt);

        when(userRepository.findUserById(userId)).thenReturn(Optional.of(sender));
        when(emergencyAlertsRepository.findFirstBySender_IdOrderByCreatedAtDescIdDesc(userId))
                .thenReturn(Optional.of(activeAlert));
        when(alertLocationsRepository.findFirstByEmergencyAlert_IdOrderByUpdatedAtDescIdDesc(100L))
                .thenReturn(Optional.of(latestLocation));

        CurrentEmergencyStatusResponse response = emergencyService.getCurrentEmergencyStatus(userId);

        assertEquals(100L, response.alertId());
        assertTrue(response.isActive());
        assertTrue(response.shouldSendLocation());
        assertEquals(lastUpdatedAt, response.lastLocationUpdatedAt());
        assertNotNull(response.nextLocationUpdateAt());
    }

    @Test
    void createsNewEmergencyWhenReusableFreeAlertExpiresWhileWaitingForLock() {
        Long userId = 1L;
        User sender = mockUser(userId, UserPlan.FREE, true);
        EmergencyAlerts expiredWhileWaiting = createAlert(50L, sender, LocalDateTime.now().minusHours(24));

        User receiver = mock(User.class);
        when(receiver.getDeletedAt()).thenReturn(null);
        when(receiver.getStatus()).thenReturn(UserStatus.ACTIVE);
        Contact contact = mock(Contact.class);
        when(contact.getContactUser()).thenReturn(receiver);

        when(userRepository.findUserById(userId)).thenReturn(Optional.of(sender));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(sender));
        when(reverseGeocodingService.findAddress(any(), any())).thenReturn(Optional.of("서울특별시 중구"));
        when(emergencyAlertsRepository.findReusableBySenderId(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(expiredWhileWaiting));
        when(contactRepository.findAllByUser_IdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(contact));
        when(emergencyAlertsRepository.save(any(EmergencyAlerts.class))).thenAnswer(invocation -> {
            EmergencyAlerts saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 51L);
            return saved;
        });

        EmergencyResponse response = emergencyService.createEmergency(
                userId,
                new EmergencyRequest(new BigDecimal("37.5665350"), new BigDecimal("126.9779692"))
        );

        assertEquals(51L, response.alertId());
        verify(emergencyAlertsRepository).save(any(EmergencyAlerts.class));
    }

    @Test
    void websocketSubscriptionRejectsWhenAlertExceeds24HoursCutoff() {
        WebSocketJwtChannelInterceptor interceptor = new WebSocketJwtChannelInterceptor(
                jwtTokenProvider,
                alertRecipientRepository
        );
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/emergency-alerts/100/location");
        accessor.setUser(UsernamePasswordAuthenticationToken.authenticated(
                new UserPrincipal(2L),
                null,
                List.of()
        ));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(alertRecipientRepository.existsActiveLocationSubscription(
                eq(100L),
                eq(2L),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(false);

        assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(message, mock(MessageChannel.class))
        );
    }

    @Test
    void controllerDelegatesGetCurrentEmergencyStatus() {
        EmergencyService mockService = mock(EmergencyService.class);
        EmergencyController controller = new EmergencyController(mockService, messagingTemplate);
        CurrentEmergencyStatusResponse expected = new CurrentEmergencyStatusResponse(
                100L,
                true,
                true,
                UserPlan.FREE,
                true,
                LocalDateTime.of(2026, 11, 7, 18, 0, 0),
                LocalDateTime.of(2026, 11, 7, 18, 0, 15)
        );
        when(mockService.getCurrentEmergencyStatus(1L)).thenReturn(expected);

        ApiResponse<CurrentEmergencyStatusResponse> apiResponse =
                controller.getCurrentEmergencyStatus(new UserPrincipal(1L));

        assertEquals(expected, apiResponse.data());
    }

    private User mockUser(Long userId, UserPlan plan, boolean realtimeLocationEnabled) {
        User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(userId);
        lenient().when(user.getPlan()).thenReturn(plan);
        lenient().when(user.isRealtimeLocationEnabled()).thenReturn(realtimeLocationEnabled);
        return user;
    }

    private EmergencyAlerts createAlert(Long alertId, User sender, LocalDateTime createdAt) {
        EmergencyAlerts alert = EmergencyAlerts.builder()
                .sender(sender)
                .latitude(new BigDecimal("37.5665350"))
                .longitude(new BigDecimal("126.9779692"))
                .address("서울특별시 중구")
                .build();
        ReflectionTestUtils.setField(alert, "id", alertId);
        ReflectionTestUtils.setField(alert, "createdAt", createdAt);
        return alert;
    }

    private AlertLocations createLocation(Long id, EmergencyAlerts alert, LocalDateTime updatedAt) {
        AlertLocations location = AlertLocations.builder()
                .emergencyAlert(alert)
                .latitude(new BigDecimal("37.5665350"))
                .longitude(new BigDecimal("126.9779692"))
                .build();
        ReflectionTestUtils.setField(location, "id", id);
        ReflectionTestUtils.setField(location, "updatedAt", updatedAt);
        ReflectionTestUtils.setField(location, "deleteAt", updatedAt.plusDays(AlertLocations.RETENTION_DAYS));
        return location;
    }
}
