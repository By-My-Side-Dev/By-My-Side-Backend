package com.gyeote.gyeote_api.global.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class WebSocketSessionLifetimeDecorator extends WebSocketHandlerDecorator {

    private static final CloseStatus MAX_SESSION_DURATION_REACHED =
            CloseStatus.NORMAL.withReason("WebSocket 연결 최대 지속 시간에 도달했습니다.");

    private final ScheduledExecutorService scheduler;
    private final Duration maxSessionDuration;
    private final Map<String, ScheduledFuture<?>> expirationTasks = new ConcurrentHashMap<>();

    public WebSocketSessionLifetimeDecorator(
            WebSocketHandler delegate,
            ScheduledExecutorService scheduler,
            Duration maxSessionDuration
    ) {
        super(delegate);
        this.scheduler = scheduler;
        this.maxSessionDuration = requirePositive(maxSessionDuration);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        ScheduledFuture<?> expirationTask = scheduler.schedule(
                () -> closeExpiredSession(session),
                maxSessionDuration.toMillis(),
                TimeUnit.MILLISECONDS
        );
        expirationTasks.put(session.getId(), expirationTask);

        try {
            super.afterConnectionEstablished(session);
        } catch (Exception exception) {
            cancelExpiration(session.getId());
            throw exception;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        cancelExpiration(session.getId());
        super.afterConnectionClosed(session, closeStatus);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn(
                "event=websocket_transport_error sessionId={} exception={}",
                session.getId(),
                WebSocketLogSupport.exceptionType(exception),
                WebSocketLogSupport.sanitized(exception)
        );
        super.handleTransportError(session, exception);
    }

    private void closeExpiredSession(WebSocketSession session) {
        expirationTasks.remove(session.getId());
        if (!session.isOpen()) {
            return;
        }

        try {
            session.close(MAX_SESSION_DURATION_REACHED);
        } catch (IOException exception) {
            log.warn("만료된 WebSocket 세션을 닫는 데 실패했습니다.: sessionId={}", session.getId(), exception);
        }
    }

    private void cancelExpiration(String sessionId) {
        ScheduledFuture<?> expirationTask = expirationTasks.remove(sessionId);
        if (expirationTask != null) {
            expirationTask.cancel(false);
        }
    }

    private static Duration requirePositive(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("WebSocket 최대 세션 지속 시간은 양수여야 합니다.");
        }
        return duration;
    }
}
