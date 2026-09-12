package com.gyeote.gyeote_api.global.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
public class WebSocketLifecycleLogger {

    private static final int NORMAL_CLOSURE = 1000;
    private static final int GOING_AWAY = 1001;

    @EventListener
    public void handleConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = WebSocketLogSupport.accessor(event.getMessage());
        String userId = WebSocketLogSupport.userId(event.getUser());
        if (WebSocketLogSupport.UNKNOWN.equals(userId)) {
            userId = WebSocketLogSupport.userId(accessor);
        }
        log.info(
                "event=stomp_connected sessionId={} userId={}",
                WebSocketLogSupport.sessionId(accessor),
                userId
        );
    }

    @EventListener
    public void handleDisconnected(SessionDisconnectEvent event) {
        int closeCode = event.getCloseStatus().getCode();
        if (closeCode == NORMAL_CLOSURE || closeCode == GOING_AWAY) {
            return;
        }

        StompHeaderAccessor accessor = WebSocketLogSupport.accessor(event.getMessage());
        String userId = WebSocketLogSupport.userId(event.getUser());
        if (WebSocketLogSupport.UNKNOWN.equals(userId)) {
            userId = WebSocketLogSupport.userId(accessor);
        }
        log.warn(
                "event=websocket_abnormal_close sessionId={} userId={} closeCode={}",
                event.getSessionId(),
                userId,
                closeCode
        );
    }
}
