package com.gyeote.gyeote_api.domain.notification.event;

import com.gyeote.gyeote_api.domain.notification.service.FcmDeliveryWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class FcmDeliveryEventListener {
    private static final Logger log = LoggerFactory.getLogger(FcmDeliveryEventListener.class);
    private final TaskExecutor executor;
    private final FcmDeliveryWorker worker;

    public FcmDeliveryEventListener(
            @Qualifier("fcmDeliveryExecutor") TaskExecutor executor,
            FcmDeliveryWorker worker
    ) {
        this.executor = executor;
        this.worker = worker;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(FcmDeliveryRequested event) {
        try {
            executor.execute(worker::drain);
        } catch (RuntimeException e) {
            log.warn("FCM 즉시 실행이 거부되었습니다. 스케줄러의 복구 필요. alertId={}", event.alertId());
        }
    }
}
