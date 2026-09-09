package com.gyeote.gyeote_api.domain.notification.service;

import com.gyeote.gyeote_api.domain.notification.config.FcmProperties;
import com.gyeote.gyeote_api.domain.notification.repository.FcmDeliveryLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 처리 가능한 FCM 발송 작업을 트랜잭션 안에서 선점하는 서비스입니다.
 *
 * <p>오래된 작업과 최대 시도 횟수를 초과한 작업을 먼저 정리한 뒤,
 * 여러 워커가 동일한 작업을 중복 처리하지 않도록 발송 대상을 선점합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class DeliveryClaimService {
    private final FcmDeliveryLogRepository repository;
    private final FcmProperties properties;

    /**
     * 현재 처리 가능한 발송 작업을 한 배치 선점합니다.
     *
     * <p>30분이 지난 작업은 만료시키고, 2분 이상 처리 상태에 머문 작업은
     * 재선점 또는 실패 처리 대상에 포함합니다.</p>
     *
     * @return 선점되어 처리 상태로 전환된 발송 작업 목록
     */
    @Transactional
    public List<DeliveryClaim> claimBatch() {
        LocalDateTime now = LocalDateTime.now();
        repository.expireOld(now, now.minusMinutes(30));
        repository.failExhaustedStale(now, now.minusMinutes(2), properties.getMaxAttempts());
        return repository.claimable(
                        now,
                        now.minusMinutes(2),
                        now.minusMinutes(30),
                        properties.getMaxAttempts(),
                        properties.getBatchSize()
                )
                .stream()
                .map(delivery -> {
                    delivery.claim(now);
                    return new DeliveryClaim(
                            delivery.getId(),
                            delivery.getAlert().getId(),
                            delivery.getRecipient().getId(),
                            delivery.getAlert().getSender().getName(),
                            delivery.getAttemptCount(),
                            delivery.getCreatedAt()
                    );
                })
                .toList();
    }
}
