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
import com.gyeote.gyeote_api.domain.notification.service.FcmOutboxService;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import com.gyeote.gyeote_api.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
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
    private final FcmOutboxService fcmOutboxService;

    // 15초 주기로 위도, 경도를 받는다.
    @Value("${emergency.location-update-interval-seconds:15}")
    private long locationUpdateIntervalSeconds;

    /**
     * 사용자의 현재 위치로 긴급 호출을 생성한다.
     * 무료 사용자는 만료되지 않은 가장 최근 알림이 있으면 해당 알림의 위치와 주소만 갱신하고,
     * 유효한 알림이 없으면 활성 상태인 지인들을 수신자로 등록한 새 알림을 생성한다.
     * 유료 사용자는 호출할 때마다 새 알림을 생성한다.
     *
     * @param userId 긴급 호출을 생성하는 사용자 ID
     * @param request 최초 위도와 경도
     * @return 생성된 긴급 호출 ID를 포함한 응답
     * @throws BusinessException 사용자를 찾을 수 없거나 호출을 받을 활성 지인이 없는 경우
     */
    @Transactional
    public EmergencyResponse createEmergency(Long userId, EmergencyRequest request) {
        User sender = userRepository.findUserById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_NOT_FOUND));

        BigDecimal latitude = normalizeCoordinate(request.latitude());
        BigDecimal longitude = normalizeCoordinate(request.longitude());
        String address = reverseGeocodingService.findAddress(latitude, longitude)
                .orElse(null);

        if (sender.getPlan() == UserPlan.FREE) {
            sender = userRepository.findByIdForUpdate(userId)
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_NOT_FOUND));

            Optional<EmergencyAlerts> reusableAlert = findReusableAlert(sender.getId(), LocalDateTime.now());
            if (reusableAlert.isPresent()) {
                return updateExistingFreeAlert(reusableAlert.get(), latitude, longitude, address);
            }
        }

        return createNewEmergency(sender, latitude, longitude, address);
    }

    private EmergencyResponse createNewEmergency(
            User sender,
            BigDecimal latitude,
            BigDecimal longitude,
            String address
    ) {
        List<User> receivers = contactRepository.findAllByUser_IdOrderByCreatedAtDesc(sender.getId()).stream()
                .map(Contact::getContactUser)
                .filter(receiver -> receiver.getDeletedAt() == null)
                .filter(receiver -> receiver.getStatus() == UserStatus.ACTIVE)
                .toList();

        if (receivers.isEmpty()) {
            throw new BusinessException(AlertErrorCode.ALERT_NO_CONTACTS);
        }

        EmergencyAlerts emergencyAlert = EmergencyAlerts.builder()
                .sender(sender)
                .latitude(latitude)
                .longitude(longitude)
                .address(address)
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

        fcmOutboxService.create(emergencyAlert, receivers);

        return EmergencyResponse.from(emergencyAlert.getId(), emergencyAlert.getAddress());
    }

    private Optional<EmergencyAlerts> findReusableAlert(Long senderId, LocalDateTime now) {
        return emergencyAlertsRepository
                .findReusableBySenderId(senderId, now, PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    private EmergencyResponse updateExistingFreeAlert(
            EmergencyAlerts emergencyAlert,
            BigDecimal latitude,
            BigDecimal longitude,
            String address
    ) {
        LocalDateTime now = LocalDateTime.now();
        emergencyAlert.updateLocation(latitude, longitude, address);

        alertLocationsRepository.findFirstByEmergencyAlert_IdOrderByUpdatedAtDescIdDesc(emergencyAlert.getId())
                .ifPresentOrElse(
                        location -> location.updateCoordinates(latitude, longitude, now),
                        () -> alertLocationsRepository.save(
                                AlertLocations.builder()
                                        .emergencyAlert(emergencyAlert)
                                        .latitude(latitude)
                                        .longitude(longitude)
                                        .build()
                        )
                );

        return EmergencyResponse.from(emergencyAlert.getId(), emergencyAlert.getAddress());
    }

    /**
     * 유료 사용자의 현재 위치를 새로운 위치 이력으로 저장한다.
     *
     * @param userId 위치를 전송하는 사용자 ID
     * @param alertId 갱신할 긴급 호출 ID
     * @param request 새 위도와 경도
     * @return 정규화된 좌표를 포함한 위치 갱신 응답
     * @throws BusinessException 호출을 찾을 수 없거나 종료된 경우, 실시간 위치 공유가 꺼진 경우,
     *                           유료 사용자가 아니거나 갱신 주기를 지키지 않은 경우
     */
    @Transactional
    public LocationUpdateResponse createPaidLocation(
            Long userId,
            Long alertId,
            LocationUpdateRequest request
    ) {
        EmergencyAlerts emergencyAlert = findUpdatableAlert(userId, alertId);
        verifyRealtimeLocationSharing(emergencyAlert.getSender());
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

    /**
     * 무료 사용자의 최신 위치 이력에 현재 좌표를 덮어쓴다.
     * 기존 위치 이력이 없으면 새로운 위치 이력을 저장한다.
     *
     * @param userId 위치를 전송하는 사용자 ID
     * @param alertId 갱신할 긴급 호출 ID
     * @param request 새 위도와 경도
     * @return 정규화된 좌표를 포함한 위치 갱신 응답
     * @throws BusinessException 호출을 찾을 수 없거나 종료된 경우, 실시간 위치 공유가 꺼진 경우,
     *                           무료 사용자가 아니거나 갱신 주기를 지키지 않은 경우
     */
    @Transactional
    public LocationUpdateResponse updateFreeLocation(
            Long userId,
            Long alertId,
            LocationUpdateRequest request
    ) {
        EmergencyAlerts emergencyAlert = findUpdatableAlert(userId, alertId);
        verifyRealtimeLocationSharing(emergencyAlert.getSender());
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
     * 동시 위치 갱신을 직렬화하기 위해 조회한 호출에 쓰기 잠금을 획득한다.
     *
     * @param userId 긴급 호출 발신자 ID
     * @param alertId 조회할 긴급 호출 ID
     * @return 위치를 갱신할 수 있는 진행 중인 긴급 호출
     * @throws BusinessException 본인 소유의 호출을 찾을 수 없거나 이미 종료된 경우
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
     * 긴급 호출 발신자의 요금제가 요청한 위치 전송 방식에 필요한 요금제인지 검증한다.
     *
     * @param emergencyAlert 위치를 갱신할 긴급 호출
     * @param requiredPlan 요청한 위치 전송 방식에 필요한 요금제
     * @throws BusinessException 발신자의 요금제가 필요한 요금제와 다를 경우
     */
    private void verifyUserPlan(EmergencyAlerts emergencyAlert, UserPlan requiredPlan) {
        if (emergencyAlert.getSender().getPlan() != requiredPlan) {
            throw new BusinessException(CommonErrorCode.COMMON_FORBIDDEN);
        }
    }

    /**
     * 긴급 호출 발신자가 실시간 위치 공유를 허용하고 있는지 검증한다.
     * 토글이 꺼져 있으면 최초 위치 이후의 위치 갱신을 허용하지 않는다.
     *
     * @param sender 실시간 위치를 공유하는 긴급 호출 발신자
     * @throws BusinessException 발신자가 실시간 위치 공유를 비활성화한 경우
     */
    private void verifyRealtimeLocationSharing(User sender) {
        if (!sender.isRealtimeLocationEnabled()) {
            throw new BusinessException(CommonErrorCode.COMMON_FORBIDDEN);
        }
    }

    /**
     * 최근 갱신 이후 설정된 간격이 지났는지 확인하고 최신 위치를 반환한다.
     *
     * @param alertId 위치 이력을 조회할 긴급 호출 ID
     * @param now 갱신 주기를 판단할 기준 시각
     * @return 최신 위치 이력. 저장된 위치가 없으면 빈 {@link Optional}
     * @throws BusinessException 최근 위치 갱신 후 허용된 간격이 지나지 않은 경우
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
     * 사용자가 생성한 진행 중인 긴급 호출을 종료한다.
     *
     * @param userId 긴급 호출 발신자 ID
     * @param alertId 종료할 긴급 호출 ID
     * @throws BusinessException 본인 소유의 호출을 찾을 수 없거나 이미 종료된 경우
     */
    @Transactional
    public void endEmergency(Long userId, Long alertId) {
        EmergencyAlerts emergencyAlert = emergencyAlertsRepository.findByIdAndSenderId(alertId, userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_NOT_FOUND));

        if (!emergencyAlert.getSender().getId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.COMMON_FORBIDDEN);
        }

        if (emergencyAlert.getEndedAt() != null) {
            throw new BusinessException(CommonErrorCode.COMMON_CONFLICT);
        }

        emergencyAlert.end(LocalDateTime.now());
    }

    /**
     * GPS 좌표를 소수점 이하 7자리로 반올림한다.
     *
     * @param coordinate 정규화할 GPS 좌표
     * @return 소수점 이하 7자리로 정규화된 좌표
     */
    private BigDecimal normalizeCoordinate(BigDecimal coordinate) {
        return coordinate.setScale(GPS_SCALE, RoundingMode.HALF_UP);
    }
}
