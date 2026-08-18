package com.gyeote.gyeote_api.domain.alert.service;

import com.gyeote.gyeote_api.domain.alert.dto.AlertHistoryResponse;
import com.gyeote.gyeote_api.domain.alert.dto.ReceivedAlertDetailResponse;
import com.gyeote.gyeote_api.domain.alert.dto.ReceivedAlertListItemResponse;
import com.gyeote.gyeote_api.domain.alert.dto.ReceivedAlertListResponse;
import com.gyeote.gyeote_api.domain.alert.dto.ReceivedAlertPageResponse;
import com.gyeote.gyeote_api.domain.alert.dto.ReceivedAlertReadResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
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

    public ReceivedAlertListResponse getReceivedAlerts(Long userId, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        var recipientPage = alertRecipientRepository.findActivePageByReceiverId(userId, now, pageable);
        List<ReceivedAlertListItemResponse> alerts = recipientPage.getContent().stream()
                .map(this::toListItem)
                .toList();

        return new ReceivedAlertListResponse(
                alerts,
                ReceivedAlertPageResponse.from(recipientPage)
        );
    }

    public ReceivedAlertDetailResponse getReceivedAlert(Long userId, Long alertId) {
        AlertRecipient recipient = requireRecipient(userId, alertId, false);
        EmergencyAlerts alert = recipient.getEmergencyAlert();
        Contact contact = contactRepository
                .findByUser_IdAndContactUser_Id(userId, alert.getSender().getId())
                .orElse(null);
        boolean realtime = isRealtimeTracking(recipient, contact, LocalDateTime.now());
        AlertLocations latestLocation = realtime
                ? alertLocationsRepository.findFirstByEmergencyAlert_IdOrderByUpdatedAtDescIdDesc(alert.getId())
                    .orElse(null)
                : null;

        return toDetail(recipient, latestLocation, realtime);
    }

    @Transactional
    public ReceivedAlertReadResponse readReceivedAlert(Long userId, Long alertId) {
        AlertRecipient recipient = requireRecipient(userId, alertId, true);
        LocalDateTime now = LocalDateTime.now();

        if (recipient.getArchivedAt() != null) {
            throw error(AlertErrorCode.ALERT_ALREADY_ARCHIVED);
        }
        if (!recipient.getExpiresAt().isAfter(now)) {
            throw error(AlertErrorCode.ALERT_ALREADY_EXPIRED);
        }
        if (recipient.isRead()) {
            throw error(AlertErrorCode.ALERT_ALREADY_READ);
        }

        recipient.read(now);

        return new ReceivedAlertReadResponse(
                alertId,
                recipient.isRead(),
                recipient.getReadAt()
        );
    }

    @Transactional
    public List<AlertHistoryResponse> getAlertHistory(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        alertRecipientRepository.archiveExpiredByReceiverId(userId, now);
        return alertRecipientRepository.findHistoryByReceiverId(userId, now).stream()
                .map(this::toHistory)
                .toList();
    }

    private AlertRecipient requireRecipient(Long userId, Long alertId, boolean forUpdate) {
        AlertRecipient recipient = (forUpdate
                ? alertRecipientRepository.findWithAlertByAlertIdAndReceiverIdForUpdate(alertId, userId)
                : alertRecipientRepository.findWithAlertByAlertIdAndReceiverId(alertId, userId))
                .orElseThrow(() -> error(AlertErrorCode.ALERT_RECIPIENT_NOT_FOUND));
        return recipient;
    }

    private ReceivedAlertListItemResponse toListItem(AlertRecipient recipient) {
        EmergencyAlerts alert = recipient.getEmergencyAlert();
        User sender = alert.getSender();
        return new ReceivedAlertListItemResponse(
                alert.getId(),
                sender.getName(),
                sender.getProfileImageUrl(),
                alert.getAddress(),
                alert.getCreatedAt(),
                recipient.isRead(),
                alert.isRealtime(),
                recipient.getExpiresAt()
        );
    }

    private ReceivedAlertDetailResponse toDetail(
            AlertRecipient recipient,
            AlertLocations latestLocation,
            boolean realtime
    ) {
        EmergencyAlerts alert = recipient.getEmergencyAlert();
        User sender = alert.getSender();
        BigDecimal latitude = latestLocation == null ? alert.getLatitude() : latestLocation.getLatitude();
        BigDecimal longitude = latestLocation == null ? alert.getLongitude() : latestLocation.getLongitude();
        LocalDateTime locationUpdatedAt = latestLocation == null ? alert.getCreatedAt() : latestLocation.getUpdatedAt();

        return new ReceivedAlertDetailResponse(
                recipient.getId(), alert.getId(), sender.getId(), sender.getName(), sender.getProfileImageUrl(),
                latitude, longitude, alert.getAddress(), realtime, recipient.isRead(), recipient.getReadAt(),
                recipient.getRealtimeStoppedAt(), alert.getEndedAt(), recipient.getCreatedAt(),
                recipient.getExpiresAt(), locationUpdatedAt
        );
    }

    private AlertHistoryResponse toHistory(AlertRecipient recipient) {
        EmergencyAlerts alert = recipient.getEmergencyAlert();
        User sender = alert.getSender();
        return new AlertHistoryResponse(
                recipient.getId(), alert.getId(), sender.getId(), sender.getName(), sender.getProfileImageUrl(),
                alert.getAddress(), recipient.isRead(), recipient.getReadAt(), recipient.getCreatedAt(),
                recipient.getExpiresAt(), recipient.getArchivedAt()
        );
    }

    private boolean isRealtimeTracking(AlertRecipient recipient, Contact contact, LocalDateTime now) {
        EmergencyAlerts alert = recipient.getEmergencyAlert();
        return alert.isRealtime()
                && alert.getEndedAt() == null
                && recipient.getArchivedAt() == null
                && recipient.getExpiresAt().isAfter(now)
                && !recipient.isRealtimeStopped()
                && contact != null
                && contact.isReceiveRealtime();
    }

    private BusinessException error(AlertErrorCode errorCode) {
        return new BusinessException(errorCode);
    }
}
