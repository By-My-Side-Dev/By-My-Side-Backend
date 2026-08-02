package com.gyeote.gyeote_api.domain.location.service;

import com.gyeote.gyeote_api.domain.alert.entity.AlertRecipients;
import com.gyeote.gyeote_api.domain.alert.repository.AlertRecipientsRepository;
import com.gyeote.gyeote_api.domain.contact.entity.Contact;
import com.gyeote.gyeote_api.domain.contact.repository.ContactRepository;
import com.gyeote.gyeote_api.domain.emergency.entity.AlertLocations;
import com.gyeote.gyeote_api.domain.emergency.entity.EmergencyAlerts;
import com.gyeote.gyeote_api.domain.emergency.repository.AlertLocationsRepository;
import com.gyeote.gyeote_api.domain.location.dto.AlertLocationResponse;
import com.gyeote.gyeote_api.domain.location.dto.AlertMapMarkerResponse;
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

    private final AlertRecipientsRepository alertRecipientsRepository;
    private final AlertLocationsRepository alertLocationsRepository;
    private final ContactRepository contactRepository;

    /**
     * 내가 받은 유효한 긴급 알림들의 위치를 지도 마커 목록으로 조회한다.
     */
    public List<AlertMapMarkerResponse> getReceivedAlertMapMarkers(Long userId) {
        List<AlertRecipients> recipients =
                alertRecipientsRepository.findActiveByReceiverId(userId, LocalDateTime.now());
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
     * 특정 수신 알림의 발신자 위치를 조회한다.
     */
    public AlertLocationResponse getReceivedAlertLocation(Long userId, Long alertRecipientId) {
        AlertRecipients recipient = alertRecipientsRepository.findWithAlertById(alertRecipientId)
                .orElseThrow(() -> new BusinessException(LocationErrorCode.LOCATION_NOT_FOUND));

        if (!recipient.getReceiver().getId().equals(userId)) {
            throw new BusinessException(LocationErrorCode.LOCATION_ACCESS_DENIED);
        }

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

    private AlertMapMarkerResponse toMarkerResponse(
            AlertRecipients recipient,
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
    private boolean isRealtimeTracking(AlertRecipients recipient, Contact contact) {
        EmergencyAlerts alert = recipient.getEmergencyAlert();
        return alert.isRealtime()
                && alert.getEndedAt() == null
                && !recipient.isRealtimeStopped()
                && (contact == null || contact.isReceiveRealtime());
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

    private Map<Long, AlertLocations> findLatestLocations(List<AlertRecipients> recipients) {
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

    private Map<Long, Contact> findContacts(Long userId, List<AlertRecipients> recipients) {
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
}