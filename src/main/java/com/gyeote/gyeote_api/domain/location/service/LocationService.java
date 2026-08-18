package com.gyeote.gyeote_api.domain.location.service;

import com.gyeote.gyeote_api.domain.contact.entity.Contact;
import com.gyeote.gyeote_api.domain.contact.repository.ContactRepository;
import com.gyeote.gyeote_api.domain.emergency.entity.AlertLocations;

import com.gyeote.gyeote_api.domain.alert.entity.AlertRecipient;
import com.gyeote.gyeote_api.domain.alert.repository.AlertRecipientRepository;
import com.gyeote.gyeote_api.domain.emergency.entity.EmergencyAlerts;
import com.gyeote.gyeote_api.domain.emergency.repository.AlertLocationsRepository;

import com.gyeote.gyeote_api.domain.location.dto.AlertLocationResponse;
import com.gyeote.gyeote_api.domain.location.dto.AlertMapMarkerResponse;
import com.gyeote.gyeote_api.domain.location.dto.AlertPathPointResponse;
import com.gyeote.gyeote_api.domain.location.dto.AlertPathResponse;
import com.gyeote.gyeote_api.domain.location.exception.LocationErrorCode;
import com.gyeote.gyeote_api.domain.user.entity.User;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 수신한 긴급 알림의 위치 조회. 발신자가 실시간 공유 중이면 최신 좌표를,
 * 그렇지 않으면 호출 시점 좌표를 반환한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationService {

    private final AlertRecipientRepository alertRecipientRepository;
    private final AlertLocationsRepository alertLocationsRepository;
    private final ContactRepository contactRepository;

    /**
     * 지인들이 발생시켜 내가 받은 유효한 긴급 알림들의 위치를 지도 마커로 조회한다.
     * 실시간 위치 수신 조건을 만족하면 최신 위치 이력을 사용하고, 그렇지 않으면
     * 긴급 호출이 생성된 시점의 좌표를 사용한다.
     *
     * @param userId 지도 위치를 조회하는 수신자 ID
     * @return 만료되거나 보관되지 않은 긴급 알림의 지도 마커 목록
     */
    public List<AlertMapMarkerResponse> getReceivedAlertMapMarkers(Long userId) {

        List<AlertRecipient> recipients =
                alertRecipientRepository.findActiveByReceiverId(userId, LocalDateTime.now());

        if (recipients.isEmpty()) {
            return List.of();
        }

        Map<Long, AlertLocations> latestLocations = findLatestLocations(recipients);
        Map<Long, Contact> contacts = findContacts(userId, recipients);

        return recipients.stream()
                .map(recipient -> toMarkerResponse(recipient, latestLocations, contacts))
                .toList();
    }

    /**
     * 지도 마커의 긴급 알림 ID로 해당 발신자의 위치 상세를 조회한다.
     *
     * @param userId 위치 상세를 조회하는 수신자 ID
     * @param alertId 전체 지도 마커 응답에 포함된 긴급 알림 ID
     * @return 현재 표시해야 하는 발신자의 위치 상세
     * @throws BusinessException 알림이 없거나 사용자가 해당 알림의 수신자가 아닌 경우
     */
    public AlertLocationResponse getReceivedAlertLocation(Long userId, Long alertId) {
        AlertRecipient recipient = requireReceivedAlert(userId, alertId);

        EmergencyAlerts alert = recipient.getEmergencyAlert();
        User sender = alert.getSender();
        boolean realtime = isRealtimeTracking(recipient, findContact(userId, sender.getId()).orElse(null));
        AlertLocations latestLocation = realtime
                ? alertLocationsRepository
                    .findFirstByEmergencyAlert_IdOrderByUpdatedAtDescIdDesc(alert.getId())
                    .orElse(null)
                : null;

        return new AlertLocationResponse(
                recipient.getId(),
                alert.getId(),
                sender.getId(),
                sender.getName(),
                sender.getProfileImageUrl(),
                latitudeOf(alert, latestLocation),
                longitudeOf(alert, latestLocation),
                alert.getAddress(),
                realtime,
                recipient.getRealtimeStoppedAt(),
                alert.getEndedAt(),
                recipient.getCreatedAt(),
                locationUpdatedAtOf(alert, latestLocation)
        );
    }

    /**
     * 지도 마커의 긴급 알림 ID로 호출 시작부터 현재까지의 이동 경로를 조회한다.
     * 저장된 위치 이력은 기록 시각과 ID의 오름차순으로 반환한다. 비실시간 호출처럼
     * 위치 이력이 없으면 긴급 호출 생성 시점의 최초 좌표를 단일 경로점으로 반환한다.
     *
     * @param userId 이동 경로를 조회하는 수신자 ID
     * @param alertId 전체 지도 마커 응답에 포함된 긴급 알림 ID
     * @return 호출 종료 시각과 시간순 위치 목록
     * @throws BusinessException 알림이 없거나 사용자가 해당 알림의 수신자가 아닌 경우
     */
    public AlertPathResponse getReceivedAlertPath(Long userId, Long alertId) {
        AlertRecipient recipient = requireReceivedAlert(userId, alertId);
        EmergencyAlerts alert = recipient.getEmergencyAlert();

        List<AlertPathPointResponse> locations = alertLocationsRepository
                .findAllByEmergencyAlert_IdOrderByUpdatedAtAscIdAsc(alertId).stream()
                .map(location -> new AlertPathPointResponse(
                        location.getId(),
                        location.getLatitude(),
                        location.getLongitude(),
                        location.getUpdatedAt()
                ))
                .toList();

        if (locations.isEmpty()) {
            locations = List.of(new AlertPathPointResponse(
                    null,
                    alert.getLatitude(),
                    alert.getLongitude(),
                    alert.getCreatedAt()
            ));
        }

        return new AlertPathResponse(alert.getId(), alert.getEndedAt(), locations);
    }

    private AlertMapMarkerResponse toMarkerResponse(
            AlertRecipient recipient,
            Map<Long, AlertLocations> latestLocations,
            Map<Long, Contact> contacts
    ) {
        EmergencyAlerts alert = recipient.getEmergencyAlert();
        User sender = alert.getSender();
        boolean realtime = isRealtimeTracking(recipient, contacts.get(sender.getId()));
        AlertLocations latestLocation = realtime ? latestLocations.get(alert.getId()) : null;

        return new AlertMapMarkerResponse(
                recipient.getId(),
                alert.getId(),
                sender.getId(),
                sender.getName(),
                sender.getProfileImageUrl(),
                latitudeOf(alert, latestLocation),
                longitudeOf(alert, latestLocation),
                alert.getAddress(),
                realtime,
                recipient.isRead(),
                recipient.getCreatedAt(),
                locationUpdatedAtOf(alert, latestLocation)
        );
    }

    /**
     * 실시간 위치로 표시할 알림인지 판단한다.
     * 호출이 실시간 모드로 생성됐고, 발신자가 공유를 끝내지 않았고,
     * 수신자도 수신을 종료하지 않았으며, 해당 지인의 실시간 수신 설정이 켜져 있어야 한다.
     */
    private boolean isRealtimeTracking(AlertRecipient recipient, Contact contact) {
        EmergencyAlerts alert = recipient.getEmergencyAlert();
        return alert.isRealtime()
                && alert.getEndedAt() == null
                && !recipient.isRealtimeStopped()
                && contact != null
                && contact.isReceiveRealtime();
    }

    private BigDecimal latitudeOf(EmergencyAlerts alert, AlertLocations latestLocation) {
        return latestLocation == null ? alert.getLatitude() : latestLocation.getLatitude();
    }

    private BigDecimal longitudeOf(EmergencyAlerts alert, AlertLocations latestLocation) {
        return latestLocation == null ? alert.getLongitude() : latestLocation.getLongitude();
    }

    private LocalDateTime locationUpdatedAtOf(EmergencyAlerts alert, AlertLocations latestLocation) {
        return latestLocation == null ? alert.getCreatedAt() : latestLocation.getUpdatedAt();
    }

    private Map<Long, AlertLocations> findLatestLocations(List<AlertRecipient> recipients) {
        List<Long> alertIds = recipients.stream()
                .map(recipient -> recipient.getEmergencyAlert().getId())
                .distinct()
                .toList();

        return alertLocationsRepository.findLatestByAlertIds(alertIds).stream()
                .collect(Collectors.toMap(
                        location -> location.getEmergencyAlert().getId(),
                        Function.identity(),
                        // 같은 시각의 행이 있으면 나중에 저장된 행을 사용한다.
                        (previous, latest) -> latest
                ));
    }

    private Map<Long, Contact> findContacts(Long userId, List<AlertRecipient> recipients) {
        List<Long> senderIds = recipients.stream()
                .map(recipient -> recipient.getEmergencyAlert().getSender().getId())
                .distinct()
                .toList();

        return contactRepository.findAllByUser_IdAndContactUser_IdIn(userId, senderIds).stream()
                .collect(Collectors.toMap(
                        contact -> contact.getContactUser().getId(),
                        Function.identity(),
                        (previous, duplicated) -> previous
                ));
    }

    private Optional<Contact> findContact(Long userId, Long senderId) {
        return contactRepository.findByUser_IdAndContactUser_Id(userId, senderId);
    }

    private AlertRecipient requireReceivedAlert(Long userId, Long alertId) {
        return alertRecipientRepository.findWithAlertByAlertIdAndReceiverId(alertId, userId)
                .orElseThrow(() -> alertRecipientRepository.existsByEmergencyAlert_Id(alertId)
                        ? new BusinessException(LocationErrorCode.LOCATION_ACCESS_DENIED)
                        : new BusinessException(LocationErrorCode.LOCATION_NOT_FOUND));
    }
}
