package com.gyeote.gyeote_api.domain.notification.service;

import com.gyeote.gyeote_api.domain.notification.config.FcmProperties;
import com.gyeote.gyeote_api.domain.notification.sender.PushDeliveryException;
import com.gyeote.gyeote_api.domain.notification.sender.PushMessage;
import com.gyeote.gyeote_api.domain.notification.sender.PushNotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 아웃박스에 적재된 FCM 발송 작업을 실제로 처리하는 워커입니다.
 *
 * <p>작업을 배치 단위로 선점한 후 토큰 존재 여부를 확인하고,
 * 발송 결과를 별도 트랜잭션으로 기록합니다.</p>
 */
@Service
public class FcmDeliveryWorker {
    private static final Logger log = LoggerFactory.getLogger(FcmDeliveryWorker.class);
    private final DeliveryClaimService claimService;
    private final DeliveryResultService resultService;
    private final PushNotificationSender sender;
    private final FcmProperties properties;

    /**
     * FCM 발송 워커를 생성합니다.
     *
     * @param claimService 발송 작업 선점 서비스
     * @param resultService 토큰 조회 및 발송 결과 처리 서비스
     * @param sender 푸시 메시지 전송기
     * @param properties FCM 실행 설정
     */
    public FcmDeliveryWorker(
            DeliveryClaimService claimService,
            DeliveryResultService resultService,
            PushNotificationSender sender,
            FcmProperties properties
    ) {
        this.claimService = claimService;
        this.resultService = resultService;
        this.sender = sender;
        this.properties = properties;
    }

    /**
     * 처리 가능한 FCM 발송 작업을 모두 소진할 때까지 배치로 처리합니다.
     *
     * <p>설정된 최대 배치 크기의 작업을 모두 처리한 경우 다음 배치를 이어서 처리합니다.</p>
     */
    public void drain() {
        List<DeliveryClaim> batch;
        do {
            batch = claimService.claimBatch();
            int succeeded = 0;
            int noRegistration = 0;
            int failed = 0;

            // 메시지 전송 개수 카운트
            for (DeliveryClaim claim : batch) {
                switch (deliver(claim)) {
                    case SUCCEEDED -> succeeded++;
                    case NO_REGISTRATION -> noRegistration++;
                    case FAILED -> failed++;
                }
            }

            if (!batch.isEmpty()) {
                log.info("FCM 배치 완료. total={}, succeeded={}, noRegistration={}, failed={}",
                        batch.size(), succeeded, noRegistration, failed);
            }
        } while (batch.size() == properties.getBatchSize());
    }

    private DeliveryOutcome deliver(DeliveryClaim claim) {
        DeviceTokenSnapshot token = resultService.findCurrentToken(claim.recipientUserId())
                .orElse(null);
        if (token == null) {
            resultService.noRegistration(claim.deliveryId());
            return DeliveryOutcome.NO_REGISTRATION;
        }

        try {
            sender.send(new PushMessage(claim.alertId(), claim.senderName(), token.token()));
            resultService.succeed(claim.deliveryId());
            return DeliveryOutcome.SUCCEEDED;
        } catch (PushDeliveryException e) {
            resultService.fail(claim.deliveryId(), token.tokenHash(), e.getErrorCode(), e.getCategory());
            if (e.getCategory() == com.gyeote.gyeote_api.domain.notification.sender.PushFailureCategory.CONFIGURATION) {
                log.error("FCM 구성 실패. alertId={}, deliveryId={}, recipientUserId={}, attempts={}, errorCode={}",
                        claim.alertId(), claim.deliveryId(), claim.recipientUserId(), claim.attemptCount(), e.getErrorCode());
            } else {
                log.warn("FCM 배송 실패. alertId={}, deliveryId={}, recipientUserId={}, attempts={}, errorCode={}",
                        claim.alertId(), claim.deliveryId(), claim.recipientUserId(), claim.attemptCount(), e.getErrorCode());
            }
            return DeliveryOutcome.FAILED;
        }
    }

    private enum DeliveryOutcome {
        SUCCEEDED,
        NO_REGISTRATION,
        FAILED
    }

}
