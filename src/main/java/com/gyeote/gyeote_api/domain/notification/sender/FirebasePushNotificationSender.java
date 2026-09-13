package com.gyeote.gyeote_api.domain.notification.sender;

import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Firebase Admin SDK를 사용하여 긴급 알림 푸시 메시지를 전송하는 구현체입니다.
 *
 * <p>Android와 iOS에 높은 우선순위와 30분의 유효 시간을 적용하며, 동일한 긴급 알림은
 * 알림 ID 기반의 collapse ID로 묶습니다. Firebase에서 발생한 예외는 처리 정책에 맞는
 * {@link PushFailureCategory}로 분류하여 {@link PushDeliveryException}으로 변환합니다.</p>
 */
@Component
@RequiredArgsConstructor
public class FirebasePushNotificationSender implements PushNotificationSender {
    /** 푸시 메시지가 유효한 시간 */
    private static final Duration TTL = Duration.ofMinutes(30);

    private final FirebaseMessaging firebaseMessaging;

    /**
     * 긴급 알림 메시지를 생성하여 FCM으로 전송합니다.
     *
     * <p>FCM 메시징 오류가 제공되면 메시징 오류 코드를 우선 사용하고, 그렇지 않으면
     * Firebase 공통 오류 코드를 사용합니다. 그 밖의 실행 중 예외는 일시적인 네트워크
     * 오류로 간주하여 재시도 가능한 실패로 변환합니다.</p>
     *
     * @param push 전송할 긴급 알림 정보와 수신자의 FCM 등록 토큰
     * @throws PushDeliveryException 메시지 생성 또는 FCM 전송에 실패한 경우
     */
    @Override
    public void send(PushMessage push) {
        try {
            firebaseMessaging.send(buildMessage(push));
        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() != null) {
                String code = e.getMessagingErrorCode().name();
                throw new PushDeliveryException(code, classifyMessaging(code), e);
            }
            String code = e.getErrorCode() == null ? "UNKNOWN" : e.getErrorCode().name();
            throw new PushDeliveryException(code, classifyCommon(code), e);
        } catch (RuntimeException e) {
            throw new PushDeliveryException("NETWORK_ERROR", PushFailureCategory.RETRYABLE, e);
        }
    }

    /**
     * 긴급 알림 정보를 Android와 iOS 설정이 포함된 FCM 메시지로 변환합니다.
     *
     * <p>Android에는 높은 우선순위, 30분 TTL, {@code emergency_alerts} 채널과 알림 ID 기반
     * 태그를 적용합니다. iOS에는 즉시 전송 우선순위, 30분 후의 만료 시각, 알림 ID 기반
     * collapse ID를 적용합니다. 공통 데이터에는 알림 유형과 알림 ID를 포함합니다.</p>
     *
     * @param push 메시지를 구성할 긴급 알림 정보와 수신자의 FCM 등록 토큰
     * @return 플랫폼별 전송 설정이 적용된 FCM 메시지
     * @throws IllegalArgumentException 메시지 구성값이 유효하지 않은 경우
     */
    public Message buildMessage(PushMessage push) {
        String collapseId = "emergency-alert-" + push.alertId();
        long expirationEpochSeconds = Instant.now().plus(TTL).getEpochSecond();
        Notification notification = Notification.builder()
                .setTitle(push.title())
                .setBody(push.body())
                .build();

        // 안드로이드 푸시 알림 빌더
        AndroidConfig android = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setTtl(TTL.toMillis())
                .setNotification(AndroidNotification.builder()
                        .setChannelId("emergency_alerts")
                        .setTag(collapseId)
                        .build())
                .build();

        // 애플 푸시 알림 빌더
        Aps aps = Aps.builder()
                .setAlert(ApsAlert.builder().setTitle(push.title()).setBody(push.body()).build())
                .build();
        ApnsConfig apns = ApnsConfig.builder()
                .putHeader("apns-priority", "10")
                .putHeader("apns-expiration", Long.toString(expirationEpochSeconds))
                .putHeader("apns-collapse-id", collapseId)
                .setAps(aps)
                .build();

        // 파이어베이스에서 제공하는 메시지 빌더 사용
        return Message.builder()
                .setToken(push.registrationToken())
                .setNotification(notification)
                .putData("type", "EMERGENCY_ALERT")
                .putData("alertId", push.alertId().toString())
                .setAndroidConfig(android)
                .setApnsConfig(apns)
                .build();
    }

    /**
     * FCM 메시징 오류 코드를 발송 실패 처리 유형으로 분류합니다.
     *
     * <p>사용할 수 없는 토큰 관련 오류는 토큰 무효로, 서버 장애나 할당량 관련 오류는
     * 재시도 가능으로, 외부 푸시 서비스 인증 오류는 설정 오류로 분류합니다.
     * 명시적으로 분류되지 않은 오류는 영구 실패로 처리합니다.</p>
     *
     * @param code FCM 메시징 오류 코드
     * @return 오류 코드에 해당하는 발송 실패 유형
     */
    private PushFailureCategory classifyMessaging(String code) {
        return switch (code) {
            case "UNREGISTERED", "SENDER_ID_MISMATCH", "INVALID_ARGUMENT" -> PushFailureCategory.TOKEN_INVALID;
            case "UNAVAILABLE", "INTERNAL", "QUOTA_EXCEEDED" -> PushFailureCategory.RETRYABLE;
            case "THIRD_PARTY_AUTH_ERROR" -> PushFailureCategory.CONFIGURATION;
            default -> PushFailureCategory.PERMANENT;
        };
    }

    /**
     * Firebase 공통 오류 코드를 발송 실패 처리 유형으로 분류합니다.
     *
     * <p>일시적인 서비스 장애, 자원 부족, 시간 초과 관련 오류는 재시도 가능으로 분류하고,
     * 인증 및 권한 오류는 설정 오류로 분류합니다. 명시적으로 분류되지 않은 오류는
     * 영구 실패로 처리합니다.</p>
     *
     * @param code Firebase 공통 오류 코드
     * @return 오류 코드에 해당하는 발송 실패 유형
     */
    private PushFailureCategory classifyCommon(String code) {
        return switch (code) {
            case "UNAVAILABLE", "INTERNAL", "RESOURCE_EXHAUSTED", "DEADLINE_EXCEEDED", "CANCELLED" -> PushFailureCategory.RETRYABLE;
            case "UNAUTHENTICATED", "PERMISSION_DENIED" -> PushFailureCategory.CONFIGURATION;
            default -> PushFailureCategory.PERMANENT;
        };
    }
}
