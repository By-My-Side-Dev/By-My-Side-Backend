package com.gyeote.gyeote_api.global.websocket;

import com.gyeote.gyeote_api.global.auth.UserPrincipal;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WebSocketLogSupport {

    public static final String UNKNOWN = "-";
    private static final int MAX_LOG_VALUE_LENGTH = 256;
    private static final Pattern EMERGENCY_DESTINATION = Pattern.compile(
            "^/(?:app|topic)/emergency-alerts/(\\d+)/location(?:/(?:paid|free))?$"
    );

    private WebSocketLogSupport() {
    }

    public static StompHeaderAccessor accessor(Message<?> message) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        return accessor != null ? accessor : StompHeaderAccessor.wrap(message);
    }

    public static String sessionId(StompHeaderAccessor accessor) {
        return valueOrUnknown(accessor.getSessionId());
    }

    public static String userId(StompHeaderAccessor accessor) {
        return userId(accessor.getUser());
    }

    public static String userId(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return String.valueOf(userPrincipal.id());
        }
        return UNKNOWN;
    }

    public static String command(StompHeaderAccessor accessor) {
        return accessor.getCommand() == null ? UNKNOWN : accessor.getCommand().name();
    }

    public static String destination(StompHeaderAccessor accessor) {
        return valueOrUnknown(accessor.getDestination());
    }

    public static String alertId(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return UNKNOWN;
        }

        Matcher matcher = EMERGENCY_DESTINATION.matcher(destination);
        return matcher.matches() ? matcher.group(1) : UNKNOWN;
    }

    public static <T extends Throwable> T findCause(Throwable exception, Class<T> type) {
        Throwable current = exception;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }
        return null;
    }

    public static String exceptionType(Throwable exception) {
        return rootCause(exception).getClass().getSimpleName();
    }

    public static Throwable sanitized(Throwable exception) {
        Throwable rootCause = rootCause(exception);
        RuntimeException sanitized = new RuntimeException(rootCause.getClass().getName());
        sanitized.setStackTrace(rootCause.getStackTrace());
        return sanitized;
    }

    private static Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String valueOrUnknown(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        String sanitized = value.replace('\r', '_').replace('\n', '_');
        return sanitized.length() <= MAX_LOG_VALUE_LENGTH
                ? sanitized
                : sanitized.substring(0, MAX_LOG_VALUE_LENGTH);
    }
}
