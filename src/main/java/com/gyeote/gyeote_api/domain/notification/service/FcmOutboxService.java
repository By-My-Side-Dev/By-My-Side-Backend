package com.gyeote.gyeote_api.domain.notification.service;

import com.gyeote.gyeote_api.domain.emergency.entity.EmergencyAlerts;
import com.gyeote.gyeote_api.domain.notification.entity.FcmDeliveryLog;
import com.gyeote.gyeote_api.domain.notification.event.FcmDeliveryRequested;
import com.gyeote.gyeote_api.domain.notification.repository.FcmDeliveryLogRepository;
import com.gyeote.gyeote_api.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 긴급 알림 수신자별 FCM 발송 작업을 아웃박스에 생성하는 서비스입니다.
 *
 * <p>긴급 알림 저장 트랜잭션 안에서 호출되어 알림과 발송 작업의 원자성을 보장하며,
 * 작업 생성 후에는 커밋 이후 발송을 시작할 수 있도록 이벤트를 발행합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class FcmOutboxService {
    private final FcmDeliveryLogRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 긴급 알림의 수신자별 FCM 발송 작업을 생성합니다.
     *
     * <p>호출자는 반드시 활성 트랜잭션을 가지고 있어야 합니다.</p>
     *
     * @param alert 발송할 긴급 알림
     * @param recipients 긴급 알림 수신자 목록
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void create(EmergencyAlerts alert, List<User> recipients) {
        List<FcmDeliveryLog> deliveries = recipients.stream()
                .map(recipient -> new FcmDeliveryLog(alert, recipient))
                .toList();
        if (deliveries.isEmpty()) return;

        repository.saveAll(deliveries);
        eventPublisher.publishEvent(new FcmDeliveryRequested(alert.getId()));
    }
}
