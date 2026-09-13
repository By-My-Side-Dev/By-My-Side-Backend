package com.gyeote.gyeote_api.domain.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 모든 실행 환경에서 FCM 아웃박스 작업을 비동기로 처리할 실행기를 구성합니다.
 */
@Configuration
@EnableConfigurationProperties(FcmProperties.class)
public class FcmExecutorConfig {

    @Bean("fcmDeliveryExecutor")
    TaskExecutor fcmDeliveryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("fcm-delivery-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
