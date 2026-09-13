package com.gyeote.gyeote_api.global.websocket;

import com.gyeote.gyeote_api.global.exception.BusinessException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

@Slf4j
@Component
public class WebSocketStompErrorHandler extends StompSubProtocolErrorHandler {

    @Override
    public Message<byte[]> handleClientMessageProcessingError(
            Message<byte[]> clientMessage,
            Throwable exception
    ) {
        if (clientMessage == null) {
            log.error(
                    "event=stomp_frame_failed sessionId=- userId=- command=- destination=- alertId=- reason=unexpected exception={}",
                    WebSocketLogSupport.exceptionType(exception),
                    WebSocketLogSupport.sanitized(exception)
            );
        } else {
            logFailure(WebSocketLogSupport.accessor(clientMessage), exception);
        }
        return super.handleClientMessageProcessingError(clientMessage, exception);
    }

    private void logFailure(StompHeaderAccessor accessor, Throwable exception) {
        if (accessor.getCommand() == StompCommand.CONNECT) {
            String reason = authenticationFailureReason(exception);
            if (reason != null) {
                log.warn(
                        "event=stomp_auth_failed sessionId={} reason={}",
                        WebSocketLogSupport.sessionId(accessor),
                        reason
                );
                return;
            }
        }

        BusinessException businessException =
                WebSocketLogSupport.findCause(exception, BusinessException.class);
        if (businessException != null) {
            logExpectedFailure(accessor, "business", businessException.getErrorCode().getCode());
            return;
        }

        if (WebSocketLogSupport.findCause(exception, AccessDeniedException.class) != null) {
            logExpectedFailure(accessor, "forbidden", WebSocketLogSupport.UNKNOWN);
            return;
        }

        if (WebSocketLogSupport.findCause(exception, MethodArgumentNotValidException.class) != null
                || WebSocketLogSupport.findCause(exception, MessageConversionException.class) != null) {
            logExpectedFailure(accessor, "validation_failed", WebSocketLogSupport.UNKNOWN);
            return;
        }

        log.error(
                "event={} sessionId={} userId={} command={} destination={} alertId={} reason=unexpected exception={}",
                failureEvent(accessor),
                WebSocketLogSupport.sessionId(accessor),
                WebSocketLogSupport.userId(accessor),
                WebSocketLogSupport.command(accessor),
                WebSocketLogSupport.destination(accessor),
                WebSocketLogSupport.alertId(accessor),
                WebSocketLogSupport.exceptionType(exception),
                WebSocketLogSupport.sanitized(exception)
        );
    }

    private void logExpectedFailure(StompHeaderAccessor accessor, String reason, String errorCode) {
        log.warn(
                "event={} sessionId={} userId={} command={} destination={} alertId={} reason={} errorCode={}",
                failureEvent(accessor),
                WebSocketLogSupport.sessionId(accessor),
                WebSocketLogSupport.userId(accessor),
                WebSocketLogSupport.command(accessor),
                WebSocketLogSupport.destination(accessor),
                WebSocketLogSupport.alertId(accessor),
                reason,
                errorCode
        );
    }

    private String authenticationFailureReason(Throwable exception) {
        if (WebSocketLogSupport.findCause(exception, ExpiredJwtException.class) != null) {
            return "expired";
        }
        if (WebSocketLogSupport.findCause(exception, JwtException.class) != null
                || WebSocketLogSupport.findCause(exception, IllegalArgumentException.class) != null) {
            return "invalid";
        }
        if (WebSocketLogSupport.findCause(exception, AccessDeniedException.class) != null) {
            return "missing";
        }
        return null;
    }

    private String failureEvent(StompHeaderAccessor accessor) {
        return accessor.getCommand() == StompCommand.SEND
                ? "stomp_send_failed"
                : "stomp_frame_failed";
    }
}
