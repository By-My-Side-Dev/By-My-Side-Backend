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

    /**
     * 실시간 위치를 갱신한다(WebSocket 전용).
     * 실시간 전송이 허용된 호출만 사용할 수 있으며, 위치 이력을 새 행으로 누적한다.
     * @param userId 유저 아이디
     * @param alertId 알림 아이디
     * @param request 현재 위치를 받는다.
     * @return 갱신된 좌표
     */
    @Transactional
    public LocationUpdateResponse updateRealtimeLocation(
            Long userId,
            Long alertId,
            LocationUpdateRequest request
    ) {
        EmergencyAlerts emergencyAlert = findUpdatableAlert(userId, alertId);

        // [실시간 위치 토글 검사] 실시간 전송이 허용되지 않은 호출은 REST 단건 갱신만 사용할 수 있다.
        if (!emergencyAlert.isRealtime()) {
            throw new BusinessException(CommonErrorCode.COMMON_FORBIDDEN);
        }

        LocalDateTime now = LocalDateTime.now();
        verifyUpdateInterval(alertId, now);

        BigDecimal latitude = normalizeCoordinate(request.latitude());
        BigDecimal longitude = normalizeCoordinate(request.longitude());

        alertLocationsRepository.save(AlertLocations.builder()
                .emergencyAlert(emergencyAlert)
                .latitude(latitude)
                .longitude(longitude)
                .build());

        return new LocationUpdateResponse(alertId, latitude, longitude);
    }

    /**
     * 현재 위치를 갱신한다(REST 전용).
     * 이력을 누적하지 않고 기존 위치 행 하나를 계속 갱신한다.
     * @param userId 유저 아이디
     * @param alertId 알림 아이디
     * @param request 현재 위치를 받는다.
     * @return 갱신된 좌표
     */
    @Transactional
    public LocationUpdateResponse updateCurrentLocation(
            Long userId,
            Long alertId,
            LocationUpdateRequest request
    ) {
        EmergencyAlerts emergencyAlert = findUpdatableAlert(userId, alertId);

        LocalDateTime now = LocalDateTime.now();
        Optional<AlertLocations> latestLocation = verifyUpdateInterval(alertId, now);

        BigDecimal latitude = normalizeCoordinate(request.latitude());
        BigDecimal longitude = normalizeCoordinate(request.longitude());

        // [실시간 위치 조회] 만약 alert_location에 행이 있는지 없는지 검사
        if (latestLocation.isPresent()) {
            // 위치 행이 있으면 같은 행을 계속해서 업데이트
            latestLocation.get().updateCoordinates(latitude, longitude, now);
        } else {
            // 위치 행이 없는 과거 호출은 최초 갱신 시 행을 보정 생성한다.
            alertLocationsRepository.save(AlertLocations.builder()
                    .emergencyAlert(emergencyAlert)
                    .latitude(latitude)
                    .longitude(longitude)
                    .build());
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
