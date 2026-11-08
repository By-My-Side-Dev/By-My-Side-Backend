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
import org.springframework.dao.CannotAcquireLockException;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

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
import static org.mockito.Mockito.times;
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

    private RecordingTransactionManager transactionManager;
    private EmergencyService emergencyService;

    @BeforeEach
    void setUp() {
        transactionManager = new RecordingTransactionManager();
        emergencyService = new EmergencyService(
                userRepository,
                emergencyAlertsRepository,
                alertLocationsRepository,
                alertRecipientRepository,
                contactRepository,
                reverseGeocodingService,
                fcmOutboxService,
                transactionManager
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
        assertEquals(UserPlan.FREE, response.plan());
        assertFalse(response.shouldSendLocation());
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
        assertEquals(UserPlan.PAID, response.plan());
        assertFalse(response.shouldSendLocation());
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
        assertEquals(UserPlan.FREE, response.plan());
        assertFalse(response.shouldSendLocation());
    }

    @Test
    void returnsActiveButShouldNotSendWhenRealtimeLocationSharingIsDisabled() {
        Long userId = 1L;
        User sender = mockUser(userId, UserPlan.FREE, false);
        LocalDateTime now = LocalDateTime.now();
        EmergencyAlerts activeAlert = createAlert(100L, sender, now.minusMinutes(30));

        when(userRepository.findUserById(userId)).thenReturn(Optional.of(sender));
        when(emergencyAlertsRepository.findFirstBySender_IdOrderByCreatedAtDescIdDesc(userId))
                .thenReturn(Optional.of(activeAlert));

        CurrentEmergencyStatusResponse response = emergencyService.getCurrentEmergencyStatus(userId);

        assertEquals(100L, response.alertId());
        assertEquals(UserPlan.FREE, response.plan());
        assertFalse(response.shouldSendLocation());
        verify(alertLocationsRepository, never())
                .findFirstByEmergencyAlert_IdOrderByUpdatedAtDescIdDesc(any());
    }

    @Test
    void returnsShouldNotSendWhenWithinUpdateInterval() {
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
        assertEquals(UserPlan.PAID, response.plan());
        assertFalse(response.shouldSendLocation());
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
        assertEquals(UserPlan.FREE, response.plan());
        assertTrue(response.shouldSendLocation());
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
        when(emergencyAlertsRepository.findUnendedAlertsBySenderIdForUpdate(userId))
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
        assertNotNull(expiredWhileWaiting.getEndedAt());
        assertEquals(1, transactionManager.begunCount.get());
        assertEquals(1, transactionManager.commitCount.get());
        assertEquals(0, transactionManager.rollbackCount.get());
        verify(emergencyAlertsRepository).save(any(EmergencyAlerts.class));
    }

    @Test
    void createsNewPaidEmergencyAndEndsPreviousUnendedAlertsWithNow() {
        Long userId = 1L;
        User sender = mockUser(userId, UserPlan.PAID, true);
        LocalDateTime previousCreatedAt = LocalDateTime.now().minusMinutes(10);
        EmergencyAlerts previousActiveAlert = createAlert(60L, sender, previousCreatedAt);

        User receiver = mock(User.class);
        when(receiver.getDeletedAt()).thenReturn(null);
        when(receiver.getStatus()).thenReturn(UserStatus.ACTIVE);
        Contact contact = mock(Contact.class);
        when(contact.getContactUser()).thenReturn(receiver);

        when(userRepository.findUserById(userId)).thenReturn(Optional.of(sender));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(sender));
        when(reverseGeocodingService.findAddress(any(), any())).thenReturn(Optional.of("서울특별시 중구"));
        when(emergencyAlertsRepository.findUnendedAlertsBySenderIdForUpdate(userId))
                .thenReturn(List.of(previousActiveAlert));
        when(contactRepository.findAllByUser_IdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(contact));
        when(emergencyAlertsRepository.save(any(EmergencyAlerts.class))).thenAnswer(invocation -> {
            EmergencyAlerts saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 61L);
            return saved;
        });

        LocalDateTime beforeCall = LocalDateTime.now();
        EmergencyResponse response = emergencyService.createEmergency(
                userId,
                new EmergencyRequest(new BigDecimal("37.5665350"), new BigDecimal("126.9779692"))
        );
        LocalDateTime afterCall = LocalDateTime.now();

        assertEquals(61L, response.alertId());
        assertNotNull(previousActiveAlert.getEndedAt());
        assertFalse(previousActiveAlert.getEndedAt().isBefore(beforeCall));
        assertFalse(previousActiveAlert.getEndedAt().isAfter(afterCall));
        assertEquals(1, transactionManager.begunCount.get());
        assertEquals(1, transactionManager.commitCount.get());
        assertEquals(0, transactionManager.rollbackCount.get());
        verify(emergencyAlertsRepository).save(any(EmergencyAlerts.class));
    }

    @Test
    void retriesCreateEmergencyOnMysqlDeadlock1213WithRollbackAndNewTransaction() {
        Long userId = 1L;
        User sender = mockUser(userId, UserPlan.PAID, true);

        User receiver = mock(User.class);
        when(receiver.getDeletedAt()).thenReturn(null);
        when(receiver.getStatus()).thenReturn(UserStatus.ACTIVE);
        Contact contact = mock(Contact.class);
        when(contact.getContactUser()).thenReturn(receiver);

        List<EmergencyAlerts> committedAlerts = new ArrayList<>();
        List<EmergencyAlerts> stagedAlerts = new ArrayList<>();
        transactionManager.onRollback = stagedAlerts::clear;
        transactionManager.onCommit = () -> {
            committedAlerts.addAll(stagedAlerts);
            stagedAlerts.clear();
        };

        when(userRepository.findUserById(userId)).thenReturn(Optional.of(sender));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(sender));
        when(reverseGeocodingService.findAddress(any(), any())).thenReturn(Optional.of("서울특별시 중구"));
        when(emergencyAlertsRepository.findUnendedAlertsBySenderIdForUpdate(userId))
                .thenReturn(List.of());
        when(contactRepository.findAllByUser_IdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(contact));
        when(emergencyAlertsRepository.save(any(EmergencyAlerts.class))).thenAnswer(invocation -> {
            EmergencyAlerts saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 70L);
            stagedAlerts.add(saved);
            return saved;
        });
        when(alertRecipientRepository.saveAll(any()))
                .thenThrow(mysqlDeadlockException())
                .thenAnswer(invocation -> invocation.getArgument(0));

        EmergencyResponse response = emergencyService.createEmergency(
                userId,
                new EmergencyRequest(new BigDecimal("37.5665350"), new BigDecimal("126.9779692"))
        );

        assertEquals(70L, response.alertId());
        assertEquals(1, committedAlerts.size());
        assertEquals(2, transactionManager.begunCount.get());
        assertEquals(1, transactionManager.rollbackCount.get());
        assertEquals(1, transactionManager.commitCount.get());
        assertTrue(transactionManager.propagations.stream()
                .allMatch(p -> p == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        verify(reverseGeocodingService, times(1)).findAddress(any(), any());
        verify(userRepository, times(2)).findByIdForUpdate(userId);
        verify(alertRecipientRepository, times(2)).saveAll(any());
    }

    @Test
    void doesNotRetryWhenCannotAcquireLockIsNotMysqlDeadlock1213() {
        Long userId = 1L;
        User sender = mockUser(userId, UserPlan.PAID, true);

        User receiver = mock(User.class);
        when(receiver.getDeletedAt()).thenReturn(null);
        when(receiver.getStatus()).thenReturn(UserStatus.ACTIVE);
        Contact contact = mock(Contact.class);
        when(contact.getContactUser()).thenReturn(receiver);

        when(userRepository.findUserById(userId)).thenReturn(Optional.of(sender));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(sender));
        when(reverseGeocodingService.findAddress(any(), any())).thenReturn(Optional.of("서울특별시 중구"));
        when(emergencyAlertsRepository.findUnendedAlertsBySenderIdForUpdate(userId))
                .thenReturn(List.of());
        when(contactRepository.findAllByUser_IdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(contact));
        when(emergencyAlertsRepository.save(any(EmergencyAlerts.class))).thenAnswer(invocation -> {
            EmergencyAlerts saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 72L);
            return saved;
        });
        SQLException lockWaitTimeout = new SQLException("Lock wait timeout exceeded", "HY000", 1205);
        when(alertRecipientRepository.saveAll(any()))
                .thenThrow(new CannotAcquireLockException("Lock wait timeout", lockWaitTimeout));

        assertThrows(
                CannotAcquireLockException.class,
                () -> emergencyService.createEmergency(
                        userId,
                        new EmergencyRequest(new BigDecimal("37.5665350"), new BigDecimal("126.9779692"))
                )
        );

        assertEquals(1, transactionManager.begunCount.get());
        assertEquals(1, transactionManager.rollbackCount.get());
        assertEquals(0, transactionManager.commitCount.get());
        verify(reverseGeocodingService, times(1)).findAddress(any(), any());
        verify(userRepository, times(1)).findByIdForUpdate(userId);
    }

    @Test
    void throwsDeadlockExceptionWhenMaxRetryAttemptsExceeded() {
        Long userId = 1L;
        User sender = mockUser(userId, UserPlan.PAID, true);

        User receiver = mock(User.class);
        when(receiver.getDeletedAt()).thenReturn(null);
        when(receiver.getStatus()).thenReturn(UserStatus.ACTIVE);
        Contact contact = mock(Contact.class);
        when(contact.getContactUser()).thenReturn(receiver);

        when(userRepository.findUserById(userId)).thenReturn(Optional.of(sender));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(sender));
        when(reverseGeocodingService.findAddress(any(), any())).thenReturn(Optional.of("서울특별시 중구"));
        when(emergencyAlertsRepository.findUnendedAlertsBySenderIdForUpdate(userId))
                .thenReturn(List.of());
        when(contactRepository.findAllByUser_IdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(contact));
        when(emergencyAlertsRepository.save(any(EmergencyAlerts.class))).thenAnswer(invocation -> {
            EmergencyAlerts saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 71L);
            return saved;
        });
        when(alertRecipientRepository.saveAll(any()))
                .thenThrow(mysqlDeadlockException());

        assertThrows(
                CannotAcquireLockException.class,
                () -> emergencyService.createEmergency(
                        userId,
                        new EmergencyRequest(new BigDecimal("37.5665350"), new BigDecimal("126.9779692"))
                )
        );

        assertEquals(3, transactionManager.begunCount.get());
        assertEquals(3, transactionManager.rollbackCount.get());
        assertEquals(0, transactionManager.commitCount.get());
        verify(reverseGeocodingService, times(1)).findAddress(any(), any());
        verify(userRepository, times(3)).findByIdForUpdate(userId);
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
                UserPlan.FREE,
                true
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

    private CannotAcquireLockException mysqlDeadlockException() {
        SQLException sqlException = new SQLException(
                "Deadlock found when trying to get lock; try restarting transaction",
                "40001",
                1213
        );
        return new CannotAcquireLockException("Could not execute statement", sqlException);
    }

    private static class RecordingTransactionManager extends AbstractPlatformTransactionManager {

        private final AtomicInteger begunCount = new AtomicInteger();
        private final AtomicInteger commitCount = new AtomicInteger();
        private final AtomicInteger rollbackCount = new AtomicInteger();
        private final List<Integer> propagations = new ArrayList<>();
        private Runnable onCommit = () -> {};
        private Runnable onRollback = () -> {};

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            begunCount.incrementAndGet();
            propagations.add(definition.getPropagationBehavior());
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commitCount.incrementAndGet();
            onCommit.run();
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbackCount.incrementAndGet();
            onRollback.run();
        }
    }
}
