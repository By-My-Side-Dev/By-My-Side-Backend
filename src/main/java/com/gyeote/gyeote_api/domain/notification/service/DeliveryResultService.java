package com.gyeote.gyeote_api.domain.notification.service;

import com.gyeote.gyeote_api.domain.device.repository.DeviceTokenRepository;
import com.gyeote.gyeote_api.domain.notification.config.FcmProperties;
import com.gyeote.gyeote_api.domain.notification.entity.DeliveryStatus;
import com.gyeote.gyeote_api.domain.notification.entity.FcmDeliveryLog;
import com.gyeote.gyeote_api.domain.notification.repository.FcmDeliveryLogRepository;
import com.gyeote.gyeote_api.domain.notification.sender.PushFailureCategory;
import com.gyeote.gyeote_api.global.security.DeviceTokenCryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * FCM 발송에 필요한 디바이스 토큰을 조회하고 발송 결과를 반영하는 서비스입니다.
 *
 * <p>각 공개 메서드는 새로운 트랜잭션에서 실행되므로 FCM을 호출하는 워커의 실행 흐름과
 * 토큰 조회 및 발송 상태 변경이 서로 분리됩니다. 발송 결과를 변경할 때는 발송 로그를
 * 비관적 잠금으로 조회하고, 현재 상태가 {@link DeliveryStatus#PROCESSING}인 경우에만
 * 결과를 반영하여 이미 완료된 작업이 중복 변경되는 것을 방지합니다.</p>
 *
 * <p>실패한 작업은 오류 유형, 최대 시도 횟수, 발송 로그 생성 후 30분의 유효 시간을
 * 기준으로 영구 실패, 만료 또는 재시도 대기 상태로 전환됩니다. 유효하지 않은 토큰은
 * 발송에 사용한 토큰과 현재 저장된 토큰의 해시가 일치할 때만 삭제합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class DeliveryResultService {
    private final FcmDeliveryLogRepository deliveryRepository;
    private final DeviceTokenRepository tokenRepository;
    private final DeviceTokenCryptoService cryptoService;
    private final FcmProperties properties;

    /**
     * 사용자의 현재 디바이스 토큰을 조회하여 발송 가능한 형태로 반환합니다.
     *
     * <p>암호화되어 저장된 토큰은 복호화하고, 실패 처리 시 토큰 변경 여부를 확인할 수 있도록
     * 토큰 해시를 함께 반환합니다.</p>
     *
     * @param userId 토큰을 조회할 사용자 ID
     * @return 토큰이 있으면 복호화된 토큰과 해시값, 없으면 빈 값
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<DeviceTokenSnapshot> findCurrentToken(Long userId) {
        return tokenRepository.findByUser_Id(userId)
                .map(token -> new DeviceTokenSnapshot(
                        cryptoService.decrypt(token.getTokenEncrypted()), token.getTokenHash()));
    }

    /**
     * 발송 작업을 {@link DeliveryStatus#SUCCEEDED} 상태로 완료합니다.
     *
     * <p>발송 로그가 존재하고 현재 {@link DeliveryStatus#PROCESSING} 상태인 경우에만
     * 성공 결과를 반영합니다.</p>
     *
     * @param deliveryId 완료할 발송 로그 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(Long deliveryId) {
        finish(deliveryId, DeliveryStatus.SUCCEEDED, null);
    }

    /**
     * 등록된 디바이스 토큰이 없는 발송 작업을
     * {@link DeliveryStatus#NO_REGISTRATION} 상태로 완료합니다.
     *
     * <p>발송 로그가 존재하고 현재 {@link DeliveryStatus#PROCESSING} 상태인 경우에만
     * 결과를 반영합니다.</p>
     *
     * @param deliveryId 완료할 발송 로그 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void noRegistration(Long deliveryId) {
        finish(deliveryId, DeliveryStatus.NO_REGISTRATION, null);
    }

    /**
     * FCM 발송 실패를 유형별로 처리합니다.
     *
     * <ul>
     *     <li>{@link PushFailureCategory#TOKEN_INVALID}: 현재 저장된 토큰과 발송에 사용한
     *     토큰이 같으면 토큰을 삭제하고 영구 실패로 종료합니다.</li>
     *     <li>{@link PushFailureCategory#PERMANENT}: 즉시 영구 실패로 종료합니다.</li>
     *     <li>{@link PushFailureCategory#RETRYABLE},
     *     {@link PushFailureCategory#CONFIGURATION}: 최대 시도 횟수와 30분의 유효 시간을
     *     초과하지 않으면 지연 시간을 계산하여 재시도 대기 상태로 변경합니다.</li>
     * </ul>
     *
     * <p>발송 로그가 없거나 현재 {@link DeliveryStatus#PROCESSING} 상태가 아니면
     * 아무 작업도 수행하지 않습니다.</p>
     *
     * @param deliveryId 실패한 발송 로그 ID
     * @param tokenHash 발송에 사용한 토큰의 해시값
     * @param errorCode FCM 또는 전송 계층에서 반환한 오류 코드
     * @param category 오류 처리 유형
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long deliveryId, String tokenHash, String errorCode, PushFailureCategory category) {
        FcmDeliveryLog delivery = deliveryRepository.findByIdForUpdate(deliveryId).orElse(null);
        if (delivery == null || delivery.getStatus() != DeliveryStatus.PROCESSING) return;

        LocalDateTime now = LocalDateTime.now();
        if (category == PushFailureCategory.TOKEN_INVALID) {
            tokenRepository.deleteIfCurrentHash(tokenHash);
            delivery.finish(DeliveryStatus.PERMANENT_FAILED, errorCode, now);
            return;
        }

        if (category == PushFailureCategory.PERMANENT) {
            delivery.finish(DeliveryStatus.PERMANENT_FAILED, errorCode, now);
            return;
        }

        if (delivery.getAttemptCount() >= properties.getMaxAttempts()) {
            delivery.finish(DeliveryStatus.PERMANENT_FAILED, errorCode, now);
            return;
        }

        LocalDateTime expiresAt = delivery.getCreatedAt().plusMinutes(30);
        LocalDateTime next = now.plus(retryDelay(delivery.getAttemptCount()));
        if (!next.isBefore(expiresAt)) {
            delivery.finish(DeliveryStatus.EXPIRED, errorCode, now);
        } else {
            delivery.retry(errorCode, next, now);
        }
    }

    /**
     * 처리 중인 발송 작업을 지정한 최종 상태로 변경합니다.
     *
     * <p>발송 로그를 비관적 잠금으로 조회하며, 로그가 없거나 이미 처리 중 상태를 벗어난 경우에는
     * 상태를 변경하지 않습니다.</p>
     *
     * @param deliveryId 완료할 발송 로그 ID
     * @param status 변경할 최종 상태
     * @param errorCode 기록할 오류 코드. 정상 완료인 경우 {@code null}
     */
    private void finish(Long deliveryId, DeliveryStatus status, String errorCode) {
        deliveryRepository.findByIdForUpdate(deliveryId)
                .filter(delivery -> delivery.getStatus() == DeliveryStatus.PROCESSING)
                .ifPresent(delivery -> delivery.finish(status, errorCode, LocalDateTime.now()));
    }

    /**
     * 현재까지 수행된 발송 시도 횟수에 따라 다음 재시도까지의 지연 시간을 계산합니다.
     *
     * <p>첫 번째 실패는 15초, 두 번째는 1분, 세 번째는 5분 뒤에 재시도하며,
     * 네 번째 이후에는 15분의 지연 시간을 적용합니다.</p>
     *
     * @param completedAttempt 현재까지 수행된 발송 시도 횟수
     * @return 다음 재시도까지의 지연 시간
     */
    private Duration retryDelay(int completedAttempt) {
        return switch (completedAttempt) {
            case 1 -> Duration.ofSeconds(15);
            case 2 -> Duration.ofMinutes(1);
            case 3 -> Duration.ofMinutes(5);
            default -> Duration.ofMinutes(15);
        };
    }

}
