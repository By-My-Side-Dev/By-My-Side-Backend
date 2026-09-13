package com.gyeote.gyeote_api.domain.notification.scheduler;

import com.gyeote.gyeote_api.domain.notification.service.FcmDeliveryWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FcmDeliveryScheduler {
    private final FcmDeliveryWorker worker;

    @Scheduled(fixedDelayString = "${fcm.scheduler.fixed-delay-ms:5000}")
    public void recoverAndDeliver() {
        worker.drain();
    }
}
