package com.gyeote.gyeote_api.global.security;

import com.gyeote.gyeote_api.domain.alert.repository.AlertRecipientRepository;
import com.gyeote.gyeote_api.domain.emergency.entity.EmergencyAlerts;
import com.gyeote.gyeote_api.global.auth.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class WebSocketJwtChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String EMERGENCY_TOPIC_PREFIX = "/topic/emergency-alerts/";
    private static final String TOPIC_PREFIX = "/topic/";
    private static final String QUEUE_PREFIX = "/queue/";
    private static final Pattern EMERGENCY_LOCATION_TOPIC =
            Pattern.compile("^/topic/emergency-alerts/(\\d+)/location$");

    private final JwtTokenProvider jwtTokenProvider;
    private final AlertRecipientRepository alertRecipientRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (accessor.getCommand() == StompCommand.CONNECT) {
            authenticate(accessor);
        }

        if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            authorizeSubscription(accessor);
        }

        if (accessor.getCommand() == StompCommand.SEND) {
            rejectDirectBrokerSend(accessor);
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);

        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException("Access token is required.");
        }

        String token = authorization.substring(BEARER_PREFIX.length());
        Long userId = jwtTokenProvider.parseUserIdOrThrow(token, JwtTokenProvider.TYPE_ACCESS);
        UserPrincipal principal = new UserPrincipal(userId);

        accessor.setUser(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of())
        );
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();

        if (destination == null || !destination.startsWith(EMERGENCY_TOPIC_PREFIX)) {
            return;
        }

        Matcher matcher = EMERGENCY_LOCATION_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            throw new AccessDeniedException("Invalid emergency location destination.");
        }

        Authentication authentication = (Authentication) accessor.getUser();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AccessDeniedException("Authentication is required.");
        }

        Long alertId = Long.parseLong(matcher.group(1));
        LocalDateTime now = LocalDateTime.now();
        if (!alertRecipientRepository.existsActiveLocationSubscription(
                alertId,
                principal.id(),
                now,
                now.minusHours(EmergencyAlerts.EXPIRE_HOURS)
        )) {
            throw new AccessDeniedException("Emergency location subscription is not allowed.");
        }
    }

    private void rejectDirectBrokerSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination != null
                && (destination.startsWith(TOPIC_PREFIX) || destination.startsWith(QUEUE_PREFIX))) {
            throw new AccessDeniedException("Direct broker destination send is not allowed.");
        }
    }
}
