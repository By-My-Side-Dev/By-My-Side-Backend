package com.gyeote.gyeote_api.global.config;

import com.gyeote.gyeote_api.global.security.WebSocketJwtChannelInterceptor;
import com.gyeote.gyeote_api.global.websocket.WebSocketSessionLifetimeDecorator;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketJwtChannelInterceptor webSocketJwtChannelInterceptor;
    private final ScheduledExecutorService webSocketSessionLifetimeScheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "websocket-session-lifetime");
                thread.setDaemon(true);
                return thread;
            });
    private final ThreadPoolTaskScheduler webSocketHeartbeatTaskScheduler =
            createWebSocketHeartbeatTaskScheduler();

    @Value("${websocket.allowed-origin-patterns}")
    private String[] allowedOriginPatterns;

    @Value("${websocket.max-session-duration:1d}")
    private Duration maxSessionDuration;

    @Value("${websocket.heartbeat.server-to-client-interval-ms:20000}")
    private long serverToClientHeartbeatIntervalMs;

    @Value("${websocket.heartbeat.client-to-server-interval-ms:20000}")
    private long clientToServerHeartbeatIntervalMs;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{
                        serverToClientHeartbeatIntervalMs,
                        clientToServerHeartbeatIntervalMs
                })
                .setTaskScheduler(webSocketHeartbeatTaskScheduler);
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketJwtChannelInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new WebSocketSessionLifetimeDecorator(
                handler,
                webSocketSessionLifetimeScheduler,
                maxSessionDuration
        ));
    }

    @PreDestroy
    void shutdownWebSocketSchedulers() {
        webSocketSessionLifetimeScheduler.shutdownNow();
        webSocketHeartbeatTaskScheduler.destroy();
    }

    private static ThreadPoolTaskScheduler createWebSocketHeartbeatTaskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("websocket-heartbeat-");
        taskScheduler.setRemoveOnCancelPolicy(true);
        taskScheduler.initialize();
        return taskScheduler;
    }
}
