package com.gyeote.gyeote_api.domain.notification.sender;

/**
 * 푸시 알림 전송 기능을 추상화한 인터페이스입니다.
 *
 * <p>구현체는 메시지와 수신자의 등록 토큰을 이용하여 외부 푸시 제공자에게
 * 전송을 요청합니다. 메서드가 정상적으로 반환되었다는 것은 푸시 제공자가
 * 전송 요청을 정상적으로 접수했다는 의미이며, 단말의 실제 수신을 보장하지는 않습니다.</p>
 *
 * <p>전송 과정에서 발생한 제공자별 오류는 {@link PushDeliveryException}으로
 * 변환하여 호출자에게 전달합니다.</p>
 */
public interface PushNotificationSender {

    /**
     * 지정된 수신자에게 푸시 메시지 전송을 요청합니다.
     *
     * @param message 알림 정보와 수신자의 등록 토큰을 포함한 메시지
     * @throws PushDeliveryException 메시지 생성 또는 외부 푸시 제공자 전송에 실패한 경우
     */
    void send(PushMessage message);
}
