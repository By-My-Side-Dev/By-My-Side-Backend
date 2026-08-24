package com.gyeote.gyeote_api.domain.emergency.service;

import com.gyeote.gyeote_api.domain.alert.exception.AlertErrorCode;
import com.gyeote.gyeote_api.domain.alert.entity.AlertRecipient;
import com.gyeote.gyeote_api.domain.alert.repository.AlertRecipientRepository;
import com.gyeote.gyeote_api.domain.contact.entity.Contact;
import com.gyeote.gyeote_api.domain.contact.repository.ContactRepository;
import com.gyeote.gyeote_api.domain.emergency.dto.EmergencyRequest;
import com.gyeote.gyeote_api.domain.emergency.dto.EmergencyResponse;
import com.gyeote.gyeote_api.domain.emergency.dto.LocationUpdateRequest;
import com.gyeote.gyeote_api.domain.emergency.dto.LocationUpdateResponse;
import com.gyeote.gyeote_api.domain.emergency.entity.AlertLocations;
import com.gyeote.gyeote_api.domain.emergency.entity.EmergencyAlerts;
import com.gyeote.gyeote_api.domain.emergency.geocoding.ReverseGeocodingService;
import com.gyeote.gyeote_api.domain.emergency.repository.AlertLocationsRepository;
import com.gyeote.gyeote_api.domain.emergency.repository.EmergencyAlertsRepository;
import com.gyeote.gyeote_api.domain.user.entity.User;
import com.gyeote.gyeote_api.domain.user.entity.UserPlan;
import com.gyeote.gyeote_api.domain.user.entity.UserStatus;
import com.gyeote.gyeote_api.domain.user.repository.UserRepository;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import com.gyeote.gyeote_api.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmergencyService {

    // 소수점 저장 길이(7자리)
    private static final int GPS_SCALE = 7;

    private final UserRepository userRepository;
    private final EmergencyAlertsRepository emergencyAlertsRepository;
    private final AlertLocationsRepository alertLocationsRepository;
    private final AlertRecipientRepository alertRecipientRepository;
    private final ContactRepository contactRepository;
    private final ReverseGeocodingService reverseGeocodingService;

    // 15초 주기로 위도, 경도를 받는다.
    @Value("${emergency.location-update-interval-seconds:15}")
    private long locationUpdateIntervalSeconds;

    /**
     * 도움 요청 생성.
     * @param userId 유저 아이디
     * @param request 위도와 경도
     * @return 알림 아이디
     */
    @Transactional
    public EmergencyResponse createEmergency(Long userId, EmergencyRequest request) {
        User sender = userRepository.findUserById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_NOT_FOUND));

        List<User> receivers = contactRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(Contact::getContactUser)
                .filter(receiver -> receiver.getDeletedAt() == null)
                .filter(receiver -> receiver.getStatus() == UserStatus.ACTIVE)
                .toList();

        if (receivers.isEmpty()) {
            throw new BusinessException(AlertErrorCode.ALERT_NO_CONTACTS);
        }

        BigDecimal latitude = normalizeCoordinate(request.latitude());
        BigDecimal longitude = normalizeCoordinate(request.longitude());
        String address = reverseGeocodingService.findAddress(latitude, longitude)
                .orElse(null);

        EmergencyAlerts emergencyAlert = EmergencyAlerts.builder()
                .sender(sender)
                .latitude(latitude)
                .longitude(longitude)
                .address(address)
                .isRealtime(sender.isRealtimeLocationEnabled())
                .build();

        emergencyAlertsRepository.save(emergencyAlert);

        List<AlertRecipient> recipients = receivers.stream()
                .map(receiver -> AlertRecipient.builder()
                        .emergencyAlert(emergencyAlert)
                        .receiver(receiver)
                        .build())
                .toList();
        alertRecipientRepository.saveAll(recipients);

        AlertLocations initialLocation = AlertLocations.builder()
                .emergencyAlert(emergencyAlert)
                .latitude(latitude)
                .longitude(longitude)
                .build();

        alertLocationsRepository.save(initialLocation);

        return EmergencyResponse.from(emergencyAlert.getId());
    }

    /** 유료 위치 전송에서 현재 위치를 새 이력 행으로 추가한다. */
    @Transactional
    public LocationUpdateResponse createPaidLocation(
            Long userId,
            Long alertId,
            LocationUpdateRequest request
    ) {
        EmergencyAlerts emergencyAlert = findUpdatableAlert(userId, alertId);
        verifyUserPlan(emergencyAlert, UserPlan.PAID);
        verifyUpdateInterval(alertId, LocalDateTime.now());

        BigDecimal latitude = normalizeCoordinate(request.latitude());
        BigDecimal longitude = normalizeCoordinate(request.longitude());

        alertLocationsRepository.save(
                AlertLocations.builder()
                        .emergencyAlert(emergencyAlert)
                        .latitude(latitude)
                        .longitude(longitude)
                        .build()
        );

        return new LocationUpdateResponse(alertId, latitude, longitude);
    }

    /** 무료 위치 전송에서 기존 위치 행의 좌표를 갱신한다. */
    @Transactional
    public LocationUpdateResponse updateFreeLocation(
            Long userId,
            Long alertId,
            LocationUpdateRequest request
    ) {
        EmergencyAlerts emergencyAlert = findUpdatableAlert(userId, alertId);
        verifyUserPlan(emergencyAlert, UserPlan.FREE);

        LocalDateTime now = LocalDateTime.now();
        Optional<AlertLocations> latestLocation = verifyUpdateInterval(alertId, now);

        BigDecimal latitude = normalizeCoordinate(request.latitude());
        BigDecimal longitude = normalizeCoordinate(request.longitude());

        if (latestLocation.isPresent()) {
            latestLocation.get().updateCoordinates(latitude, longitude, now);
        } else {
            alertLocationsRepository.save(
                    AlertLocations.builder()
                            .emergencyAlert(emergencyAlert)
                            .latitude(latitude)
                            .longitude(longitude)
                            .build()
            );
        }

        return new LocationUpdateResponse(alertId, latitude, longitude);
    }

    /**
     * 위치를 갱신할 수 있는 본인 소유의 진행 중인 호출을 조회한다.
     */
    private EmergencyAlerts findUpdatableAlert(Long userId, Long alertId) {
        EmergencyAlerts emergencyAlert = emergencyAlertsRepository.findByIdAndSenderIdForUpdate(alertId, userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_NOT_FOUND));

        if (emergencyAlert.getEndedAt() != null) {
            throw new BusinessException(CommonErrorCode.COMMON_CONFLICT);
        }

        return emergencyAlert;
    }

    private void verifyUserPlan(EmergencyAlerts emergencyAlert, UserPlan requiredPlan) {
        if (emergencyAlert.getSender().getPlan() != requiredPlan) {
            throw new BusinessException(CommonErrorCode.COMMON_FORBIDDEN);
        }
    }

    /**
     * 최근 갱신 이후 설정된 간격이 지났는지 확인하고 최신 위치를 반환한다.
     */
    private Optional<AlertLocations> verifyUpdateInterval(Long alertId, LocalDateTime now) {
        Optional<AlertLocations> latestLocation = alertLocationsRepository.findFirstByEmergencyAlert_IdOrderByUpdatedAtDescIdDesc(alertId);

        latestLocation.filter(latest -> latest.getUpdatedAt()
                        .plusSeconds(locationUpdateIntervalSeconds)
                        .isAfter(now))
                .ifPresent(latest -> {
                    throw new BusinessException(CommonErrorCode.COMMON_TOO_MANY_REQUESTS);
                });

        return latestLocation;
    }

    /**
     * 도움 요청 종료.
     * @param userId 유저 아이디
     * @param alertId 알림 아이디
     */
    @Transactional
    public void endEmergency(Long userId, Long alertId) {
        EmergencyAlerts emergencyAlert = emergencyAlertsRepository.findByIdAndSenderId(alertId, userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_NOT_FOUND));

        if (!emergencyAlert.getSender().getId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.COMMON_FORBIDDEN);
        }

        if (emergencyAlert.isRealtime()) {
            if (emergencyAlert.getEndedAt() != null) {
                throw new BusinessException(CommonErrorCode.COMMON_CONFLICT);
            }

            emergencyAlert.end(LocalDateTime.now());
        }
    }

    /**
     * 7자리 정수로 코드 정규화
     * @param coordinate 입력된 소수점
     * @return 7자리 소수점으로 반환
     */
    private BigDecimal normalizeCoordinate(BigDecimal coordinate) {
        return coordinate.setScale(GPS_SCALE, RoundingMode.HALF_UP);
    }
}
