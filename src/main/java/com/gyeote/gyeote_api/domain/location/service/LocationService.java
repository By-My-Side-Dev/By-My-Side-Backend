package com.gyeote.gyeote_api.domain.location.service;

import com.gyeote.gyeote_api.domain.contact.entity.Contact;
import com.gyeote.gyeote_api.domain.contact.repository.ContactRepository;
import com.gyeote.gyeote_api.domain.emergency.entity.AlertLocations;

import com.gyeote.gyeote_api.domain.alert.entity.AlertRecipient;
import com.gyeote.gyeote_api.domain.alert.repository.AlertRecipientRepository;
import com.gyeote.gyeote_api.domain.emergency.entity.EmergencyAlerts;
import com.gyeote.gyeote_api.domain.emergency.repository.AlertLocationsRepository;

import com.gyeote.gyeote_api.domain.location.dto.AlertMapMarkerResponse;
import com.gyeote.gyeote_api.domain.location.dto.AlertPathPointResponse;
import com.gyeote.gyeote_api.domain.location.dto.AlertPathResponse;
import com.gyeote.gyeote_api.domain.location.exception.LocationErrorCode;
import com.gyeote.gyeote_api.domain.user.entity.User;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import com.gyeote.gyeote_api.global.storage.ProfileImageUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationService {

    private final AlertRecipientRepository alertRecipientRepository;
    private final AlertLocationsRepository alertLocationsRepository;
    private final ContactRepository contactRepository;
    private final ProfileImageUrlResolver profileImageUrlResolver;

    /**
     * 지인들이 발생시켜 내가 받은 유효한 긴급 알림들의 위치를 지도 마커로 조회한다.
     * 모든 호출의 최신 위치 행을 사용한다.
     * realtime 표시는 수신자의 실시간 수신 조건을 따로 반영한다.
     *
     * @param userId 지도 위치를 조회하는 수신자 아이디
     * @return 만료되거나 보관되지 않은 긴급 알림의 지도 마커 목록
     */
    public List<AlertMapMarkerResponse> getReceivedAlertMapMarkers(Long userId) {

        List<AlertRecipient> recipients = alertRecipientRepository.findActiveByReceiverId(userId, LocalDateTime.now());

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
     * 지도 마커의 긴급 알림 ID로 호출 시작부터 현재까지의 이동 경로를 조회한다.
     * 저장된 위치 이력은 기록 시각과 ID의 오름차순으로 반환한다. 비실시간 호출처럼
     * 위치 이력이 없으면 긴급 호출 생성 시점의 최초 좌표를 단일 경로점으로 반환한다.
     *
     * @param userId 이동 경로를 조회하는 수신자 아이디
     * @param alertId 전체 지도 마커 응답에 포함된 긴급 알림 아이디
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

    /**
     * 알림 수신 정보를 지도 마커 응답으로 변환한다.
     * 최신 위치가 없으면 알림 발생 시점의 위치를 사용한다.
     *
     * @param recipient 변환할 알림 수신 정보
     * @param latestLocations 알림 아이디별 최신 위치 정보
     * @param contacts 발신자 아이디별 연락처 정보
     * @return 지도에 표시할 알림 마커 응답
     */
    private AlertMapMarkerResponse toMarkerResponse(
            AlertRecipient recipient,
            Map<Long, AlertLocations> latestLocations,
            Map<Long, Contact> contacts
    ) {
        EmergencyAlerts alert = recipient.getEmergencyAlert();
        User sender = alert.getSender();
        boolean realtime = isRealtimeTracking(recipient, contacts.get(sender.getId()));
        AlertLocations latestLocation = latestLocations.get(alert.getId());
        String profileImageUrl = profileImageUrlResolver.resolve(sender);

        return new AlertMapMarkerResponse(
                recipient.getId(),
                alert.getId(),
                sender.getId(),
                sender.getName(),
                profileImageUrl,
                latitudeOf(alert, latestLocation),
                longitudeOf(alert, latestLocation),
                alert.getAddress(),
                realtime,
                recipient.getCreatedAt(),
                locationUpdatedAtOf(alert, latestLocation)
        );
    }

    /**
     * 실시간 위치로 표시할 알림인지 판단한다.
     * 발신자의 실시간 위치 공유 설정이 켜져 있고, 발신자가 공유를 끝내지 않았고,
     * 수신자도 수신을 종료하지 않았으며, 해당 지인의 실시간 수신 설정이 켜져 있어야 한다.
     *
     * @param recipient 확인할 알림 수신 정보
     * @param contact 발신자와 연결된 연락처 정보
     * @return 실시간 위치를 표시할 수 있으면 {@code true}, 아니면 {@code false}
     */
    private boolean isRealtimeTracking(AlertRecipient recipient, Contact contact) {
        EmergencyAlerts alert = recipient.getEmergencyAlert();
        return alert.getSender().isRealtimeLocationEnabled()
                && alert.getEndedAt() == null
                && !recipient.isRealtimeStopped()
                && contact != null
                && contact.isReceiveRealtime();
    }

    /**
     * 최신 위치가 있으면 해당 위도를, 없으면 알림 발생 위치의 위도를 반환한다.
     *
     * @param alert 위치를 조회할 긴급 알림
     * @param latestLocation 긴급 알림의 최신 위치
     * @return 지도에 표시할 위도
     */
    private BigDecimal latitudeOf(EmergencyAlerts alert, AlertLocations latestLocation) {
        return latestLocation == null ? alert.getLatitude() : latestLocation.getLatitude();
    }

    /**
     * 최신 위치가 있으면 해당 경도를, 없으면 알림 발생 위치의 경도를 반환한다.
     *
     * @param alert 위치를 조회할 긴급 알림
     * @param latestLocation 긴급 알림의 최신 위치
     * @return 지도에 표시할 경도
     */
    private BigDecimal longitudeOf(EmergencyAlerts alert, AlertLocations latestLocation) {
        return latestLocation == null ? alert.getLongitude() : latestLocation.getLongitude();
    }

    /**
     * 최신 위치가 있으면 갱신 시각을, 없으면 알림 생성 시각을 반환한다.
     *
     * @param alert 시각을 조회할 긴급 알림
     * @param latestLocation 긴급 알림의 최신 위치
     * @return 위치가 마지막으로 갱신된 시각
     */
    private LocalDateTime locationUpdatedAtOf(EmergencyAlerts alert, AlertLocations latestLocation) {
        return latestLocation == null ? alert.getCreatedAt() : latestLocation.getUpdatedAt();
    }

    /**
     * 수신 알림별 최신 위치를 한 번에 조회하여 알림 아이디 기준으로 구성한다.
     *
     * @param recipients 최신 위치를 조회할 알림 수신 정보 목록
     * @return 알림 아이디를 키로 갖는 최신 위치 맵
     */
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

    /**
     * 수신자와 각 알림 발신자 사이의 연락처 정보를 한 번에 조회한다.
     *
     * @param userId 알림을 수신한 사용자 아이디
     * @param recipients 발신자를 추출할 알림 수신 정보 목록
     * @return 발신자 아이디를 키로 갖는 연락처 맵
     */
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

    /**
     * 사용자가 수신한 특정 긴급 알림을 조회한다.
     *
     * @param userId 알림을 수신한 사용자 아이디
     * @param alertId 조회할 긴급 알림 아이디
     * @return 수신 정보가 확인된 긴급 알림
     * @throws BusinessException 알림이 없거나 사용자가 해당 알림의 수신자가 아닌 경우
     */
    private AlertRecipient requireReceivedAlert(Long userId, Long alertId) {
        return alertRecipientRepository.findWithAlertByAlertIdAndReceiverId(alertId, userId)
                .orElseThrow(() -> alertRecipientRepository.existsByEmergencyAlert_Id(alertId)
                        ? new BusinessException(LocationErrorCode.LOCATION_ACCESS_DENIED)
                        : new BusinessException(LocationErrorCode.LOCATION_NOT_FOUND));
    }
}
