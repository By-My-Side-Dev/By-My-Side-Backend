package com.gyeote.gyeote_api.domain.alert.service;

import com.gyeote.gyeote_api.domain.alert.dto.AlertHistoryResponse;
import com.gyeote.gyeote_api.domain.alert.dto.ReceivedAlertDetailResponse;
import com.gyeote.gyeote_api.domain.alert.dto.ReceivedAlertListItemResponse;
import com.gyeote.gyeote_api.domain.alert.dto.ReceivedAlertListResponse;
import com.gyeote.gyeote_api.domain.alert.entity.AlertRecipient;
import com.gyeote.gyeote_api.domain.alert.exception.AlertErrorCode;
import com.gyeote.gyeote_api.domain.alert.repository.AlertRecipientRepository;
import com.gyeote.gyeote_api.domain.contact.entity.Contact;
import com.gyeote.gyeote_api.domain.contact.repository.ContactRepository;
import com.gyeote.gyeote_api.domain.emergency.entity.AlertLocations;
import com.gyeote.gyeote_api.domain.emergency.entity.EmergencyAlerts;
import com.gyeote.gyeote_api.domain.emergency.repository.AlertLocationsRepository;
import com.gyeote.gyeote_api.domain.user.entity.User;
import com.gyeote.gyeote_api.global.exception.BusinessException;
import com.gyeote.gyeote_api.global.storage.ProfileImageUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertService {

    private final AlertRecipientRepository alertRecipientRepository;
    private final AlertLocationsRepository alertLocationsRepository;
    private final ContactRepository contactRepository;
    private final ProfileImageUrlResolver profileImageUrlResolver;

    /**
     * 사용자가 수신한 활성 알림 목록을 조회한다.
     *
     * @param userId 알림을 수신한 사용자 아이디
     * @return 현재 활성 상태인 수신 알림 목록
     */
    public ReceivedAlertListResponse getReceivedAlerts(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<ReceivedAlertListItemResponse> alerts = alertRecipientRepository
                .findActiveByReceiverId(userId, now).stream()
                .map(this::toListItem)
                .toList();

        return new ReceivedAlertListResponse(alerts);
    }

    /**
     * 사용자가 수신한 특정 알림의 상세 정보를 조회한다.
     *
     * @param userId 알림을 수신한 사용자 아이디
     * @param alertId 조회할 알림 아이디
     * @return 위치와 실시간 추적 상태를 포함한 알림 상세 정보
     */
    public ReceivedAlertDetailResponse getReceivedAlert(Long userId, Long alertId) {
        AlertRecipient recipient = requireRecipient(userId, alertId);
        EmergencyAlerts alert = recipient.getEmergencyAlert();
        Contact contact = contactRepository
                .findByUser_IdAndContactUser_Id(userId, alert.getSender().getId())
                .orElse(null);
        LocalDateTime now = LocalDateTime.now();
        boolean realtime = isRealtimeTracking(recipient, contact, now);
        AlertLocations latestLocation = alertLocationsRepository
                .findFirstByEmergencyAlert_IdOrderByUpdatedAtDescIdDesc(alert.getId())
                .orElse(null);

        return toDetail(recipient, latestLocation, realtime, now);
    }

    /**
     * 만료된 수신 알림을 보관 처리하고 사용자의 알림 이력을 조회한다.
     *
     * @param userId 알림 이력을 조회할 사용자 아이디
     * @return 사용자의 알림 이력 목록
     */
    @Transactional
    public List<AlertHistoryResponse> getAlertHistory(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        alertRecipientRepository.archiveExpiredByReceiverId(userId, now);
        return alertRecipientRepository.findHistoryByReceiverId(userId, now).stream()
                .map(this::toHistory)
                .toList();
    }

    /**
     * 사용자와 알림 아이디에 해당하는 수신 정보를 조회한다.
     *
     * @param userId 알림을 수신한 사용자 아이디
     * @param alertId 조회할 알림 아이디
     * @return 발신 알림 정보가 함께 조회된 수신 정보
     */
    private AlertRecipient requireRecipient(Long userId, Long alertId) {
        AlertRecipient recipient = alertRecipientRepository
                .findWithAlertByAlertIdAndReceiverId(alertId, userId)
                .orElseThrow(() -> new BusinessException(AlertErrorCode.ALERT_RECIPIENT_NOT_FOUND));
        return recipient;
    }

    /**
     * 알림 수신 정보를 목록 항목 응답으로 변환한다.
     *
     * @param recipient 변환할 알림 수신 정보
     * @return 수신 알림 목록 항목
     */
    private ReceivedAlertListItemResponse toListItem(AlertRecipient recipient) {
        EmergencyAlerts alert = recipient.getEmergencyAlert();
        User sender = alert.getSender();
        String profileImageUrl = profileImageUrlResolver.resolve(sender);
        return new ReceivedAlertListItemResponse(
                alert.getId(),
                sender.getName(),
                profileImageUrl,
                alert.getAddress(),
                alert.getCreatedAt(),
                sender.isRealtimeLocationEnabled(),
                recipient.getExpiresAt()
        );
    }

    /**
     * 알림 수신 정보와 최신 위치를 상세 응답으로 변환한다.
     * 최신 위치가 없으면 알림 발생 시점의 위치를 사용한다.
     *
     * @param recipient 변환할 알림 수신 정보
     * @param latestLocation 알림 발신자의 최신 위치 정보
     * @param realtime 현재 실시간 위치를 추적할 수 있는지 여부
     * @return 수신 알림 상세 정보
     */
    private ReceivedAlertDetailResponse toDetail(
            AlertRecipient recipient,
            AlertLocations latestLocation,
            boolean realtime,
            LocalDateTime now
    ) {
        EmergencyAlerts alert = recipient.getEmergencyAlert();
        User sender = alert.getSender();

        BigDecimal latitude = latestLocation == null
                ? alert.getLatitude()
                : latestLocation.getLatitude();

        BigDecimal longitude = latestLocation == null
                ? alert.getLongitude()
                : latestLocation.getLongitude();

        LocalDateTime locationUpdatedAt = latestLocation == null
                ? alert.getCreatedAt()
                : latestLocation.getUpdatedAt();

        String profileImageUrl = profileImageUrlResolver.resolve(sender);

        return new ReceivedAlertDetailResponse(
                recipient.getId(),
                alert.getId(),
                sender.getId(),
                sender.getName(),
                profileImageUrl,
                latitude,
                longitude,
                alert.getAddress(),
                realtime,
                recipient.getRealtimeStoppedAt(),
                alert.resolveEndedAt(now),
                recipient.getCreatedAt(),
                recipient.getExpiresAt(),
                locationUpdatedAt
        );
    }

    /**
     * 알림 수신 정보를 알림 이력 응답으로 변환한다.
     *
     * @param recipient 변환할 알림 수신 정보
     * @return 알림 이력 응답
     */
    private AlertHistoryResponse toHistory(AlertRecipient recipient) {
        EmergencyAlerts alert = recipient.getEmergencyAlert();
        User sender = alert.getSender();
        String profileImageUrl = profileImageUrlResolver.resolve(sender);
        return new AlertHistoryResponse(
                recipient.getId(),
                alert.getId(),
                sender.getId(),
                sender.getName(),
                profileImageUrl,
                alert.getAddress(),
                recipient.getCreatedAt(),
                recipient.getExpiresAt(),
                recipient.getArchivedAt()
        );
    }

    /**
     * 알림 발신자의 위치를 실시간으로 추적할 수 있는지 확인한다.
     * 발신자의 위치 공유 설정, 알림과 수신 정보의 상태 및 연락처 수신 설정을 모두 검사한다.
     *
     * @param recipient 확인할 알림 수신 정보
     * @param contact 발신자와 연결된 연락처 정보
     * @param now 추적 가능 시간을 판단할 기준 시각
     * @return 실시간 위치를 추적할 수 있으면 {@code true}, 아니면 {@code false}
     */
    private boolean isRealtimeTracking(AlertRecipient recipient, Contact contact, LocalDateTime now) {
        EmergencyAlerts alert = recipient.getEmergencyAlert();
        return alert.getSender().isRealtimeLocationEnabled()
                && !alert.isEnded(now)
                && recipient.getArchivedAt() == null
                && recipient.getExpiresAt().isAfter(now)
                && !recipient.isRealtimeStopped()
                && contact != null
                && contact.isReceiveRealtime();
    }

}
